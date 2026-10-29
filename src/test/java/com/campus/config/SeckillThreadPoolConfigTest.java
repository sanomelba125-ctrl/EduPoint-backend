package com.campus.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 秒杀线程池的 MDC 透传契约测试（2026-10-29，P1-5）。
 *
 * <p>背景：{@code TraceIdFilter} 把 traceId 放进 Tomcat 线程的 MDC，秒杀请求在
 * {@code SeckillController} 里用 {@code CompletableFuture.supplyAsync(..., seckillThreadPoolTaskExecutor)}
 * 切到这个线程池。线程池不配置 {@code TaskDecorator} 时，工作线程的 MDC 恒为空，
 * 于是 {@code StudentGiftServiceImpl:1044} 的 {@code MDC.get("traceId")} 拿到 null，
 * {@code local_message.trace_id} 落空、{@code RabbitMqConfig} 的
 * {@code beforePublishPostProcessor} 也填不上 MQ 头 {@code x-trace-id}，整条异步链路断链。
 *
 * <p>这里直接驱动真实的线程池（而不是 mock），断言三件事：
 * ① 子线程能读到提交线程的 traceId；② 真的跑在 {@code seckill-async-*} 线程上；
 * ③ 任务内改 MDC 不会污染池化线程，也不会串到下一个任务。
 */
class SeckillThreadPoolConfigTest {

    private static final String TRACE_KEY = "traceId";

    private ThreadPoolTaskExecutor executor;

    @BeforeEach
    void setUp() {
        SeckillThreadPoolConfig config = new SeckillThreadPoolConfig();
        ReflectionTestUtils.setField(config, "coreSize", 2);
        ReflectionTestUtils.setField(config, "maxSize", 4);
        ReflectionTestUtils.setField(config, "queueCapacity", 10);
        executor = config.seckillThreadPoolTaskExecutor();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("P1-5：提交时带 traceId，工作线程必须能读到，并且确实跑在 seckill-async 线程上")
    void traceIdMustPropagateToWorkerThread() throws Exception {
        MDC.put(TRACE_KEY, "TR-P15-0001");

        String seenTraceId = executor.submit(() -> MDC.get(TRACE_KEY)).get(3, TimeUnit.SECONDS);
        String seenThreadName = executor.submit(() -> Thread.currentThread().getName()).get(3, TimeUnit.SECONDS);

        assertEquals("TR-P15-0001", seenTraceId,
                "工作线程读不到 traceId：线程池缺少 TaskDecorator，local_message.trace_id 与 MQ 头都会是 null");
        assertNotNull(seenThreadName);
        assertTrue(seenThreadName.startsWith("seckill-async-"),
                "任务必须真正跑在秒杀专用线程池上，否则本用例测不到跨线程场景，实际线程=" + seenThreadName);
    }

    @Test
    @DisplayName("P1-5：任务内改写 MDC 不得污染池化线程，也不得串到下一个任务")
    void mdcMustNotLeakBetweenTasks() throws Exception {
        MDC.put(TRACE_KEY, "TR-P15-0002");

        // 第一个任务把 MDC 改成别的值
        executor.submit(() -> {
            MDC.put(TRACE_KEY, "LEAKED");
            return null;
        }).get(3, TimeUnit.SECONDS);

        // 提交线程自身的上下文不受影响
        assertEquals("TR-P15-0002", MDC.get(TRACE_KEY), "子线程改 MDC 不能反向影响调用线程");

        // 第二个任务仍应看到提交线程的快照，而不是上一个任务留下的 LEAKED
        String secondSeen = executor.submit(() -> MDC.get(TRACE_KEY)).get(3, TimeUnit.SECONDS);
        assertEquals("TR-P15-0002", secondSeen, "上一个任务的 MDC 残留串到了下一个任务");

        // 提交线程没有 traceId 时，工作线程也必须是干净的
        MDC.clear();
        String emptySeen = executor.submit(() -> MDC.get(TRACE_KEY)).get(3, TimeUnit.SECONDS);
        assertNull(emptySeen, "提交方没有 traceId 时，工作线程不应凭空继承到旧值");
    }
}
