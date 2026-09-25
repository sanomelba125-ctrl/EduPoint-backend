package com.campus.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 秒杀高并发业务专用线程池资源隔离配置 (P6-1)
 * 物理隔离常规业务与秒杀抢兑业务，防止秒杀洪峰打满通用线程池导致常规业务不可用
 */
@Configuration
public class SeckillThreadPoolConfig {

    @Bean("seckillThreadPoolTaskExecutor")
    public ThreadPoolTaskExecutor seckillThreadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(1000);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("seckill-async-");
        // 拒绝策略：调用者运行策略 (CallerRunsPolicy)，平滑降级并反压调用方
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
