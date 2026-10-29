package com.campus.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.Assert;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 秒杀高并发业务专用线程池资源隔离配置 (P6-1)
 * 物理隔离常规业务与秒杀抢兑业务，防止秒杀洪峰打满通用线程池导致常规业务不可用。
 *
 * <p><b>容量口径</b>：单笔秒杀同步段（2 次 MySQL 查询 + 1 次发件箱 INSERT + 4 次 Redis + 1 次 MQ 投递）
 * 实测约 60ms，单线程吞吐约 16 次/秒。核心线程数必须与入口限流对齐：
 * 每用户 2 次/秒 × 100 个在抢用户 = 200 次/秒的受理上限，则至少需要 200 ÷ 16 ≈ 13 个线程才能不排队；
 * 取 32 留 2.4 倍突发余量。详见 benchmark/PERFORMANCE_TEST_REPORT_20261028.md §5。
 *
 * <p><b>为什么 maxPoolSize 长期是「死配置」</b>：{@link ThreadPoolExecutor} 的扩容顺序是
 * 「核心线程满 → 先塞队列 → 队列满才扩到 max」。原配置核心 8、队列 1000、max 16，
 * 意味着只有当 1000 个任务堆积时才会创建第 9 个线程，洪峰期实际并发恒为 8。
 * 压测中观测到的 125 次/秒受理速率正是「8 × 16」的上限，与实测完全吻合。
 * 因此本次把核心线程数作为唯一有效旋钮调整，队列仍保留 1000 作为突发缓冲。
 */
@Slf4j
@Configuration
public class SeckillThreadPoolConfig {

    /** 核心线程数：稳态并发，决定洪峰期实际并发度 */
    @Value("${campus.seckill.pool.core-size:32}")
    private int coreSize;

    /** 最大线程数：仅在队列积压满 1000 后才会启用，作为最后的扩容余量 */
    @Value("${campus.seckill.pool.max-size:64}")
    private int maxSize;

    /** 队列容量：突发缓冲，必须显式声明，避免默认 Integer.MAX_VALUE 导致 max 永不生效 */
    @Value("${campus.seckill.pool.queue-capacity:1000}")
    private int queueCapacity;

    @Bean("seckillThreadPoolTaskExecutor")
    public ThreadPoolTaskExecutor seckillThreadPoolTaskExecutor() {
        Assert.isTrue(maxSize >= coreSize,
                "campus.seckill.pool.max-size(" + maxSize + ") 不能小于 core-size(" + coreSize + ")");
        Assert.isTrue(queueCapacity > 0, "campus.seckill.pool.queue-capacity 必须为正数");

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("seckill-async-");
        // 跨线程传递 MDC traceId（2026-10-29 P1-5）。
        // 请求进入时 TraceIdFilter 把 traceId 放进 Tomcat 线程的 MDC，而秒杀在这里切到
        // seckill-async-* 线程池执行；不装 TaskDecorator 的话工作线程 MDC 恒为空，
        // 于是 local_message.trace_id 落 null、MQ 消息头 x-trace-id 也不会被
        // RabbitMqConfig 的 beforePublishPostProcessor 填上，整条异步链路断链。
        // TraceUtil.wrap 会捕获提交时的 MDC 快照、执行完恢复原值，不会污染池化线程。
        executor.setTaskDecorator(com.campus.common.util.TraceUtil::wrap);
        // 拒绝策略：调用者运行策略 (CallerRunsPolicy)，平滑降级并反压调用方
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 预热核心线程：避免爬坡阶段现建线程，把建线程开销计入首屏请求延迟
        executor.setPrestartAllCoreThreads(true);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        log.info("[秒杀线程池] 初始化: core={}, max={}, queue={}, keepAlive=60s, prestart=true, reject=CallerRunsPolicy",
                coreSize, maxSize, queueCapacity);
        return executor;
    }
}
