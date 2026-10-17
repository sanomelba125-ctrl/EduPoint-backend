package com.campus.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 生产级 Prometheus 自定义业务指标采集器 (P3-1)
 * 暴露核心业务指征：礼品兑换量、成功率、P99 延迟、活动打卡耗时与死信超时关单计数
 */
@Component
public class CampusMetricsCollector {

    private final MeterRegistry registry;

    // 1. 商城礼品兑换指标
    private final Counter exchangeTotalCounter;
    private final Counter exchangeSuccessCounter;
    private final Counter exchangeFailureCounter;
    private final Timer exchangeDurationTimer;

    // 2. 活动签到打卡指标
    private final Counter checkinTotalCounter;
    private final Counter checkinSuccessCounter;
    private final Counter checkinFailureCounter;
    private final Timer checkinDurationTimer;

    // 3. 延时死信关单指标
    private final Counter orderExpireCounter;

    // 4. 本地发件箱 Outbox 异常/判死指标 (P1-D)
    private final Counter outboxDeadCounter;
    private final Counter outboxRetryCounter;

    public CampusMetricsCollector(MeterRegistry registry) {
        this.registry = registry;

        // 初始化礼品兑换指标
        this.exchangeTotalCounter = Counter.builder("campus.mall.exchange.total")
                .description("商城礼品兑换请求总量")
                .register(registry);

        this.exchangeSuccessCounter = Counter.builder("campus.mall.exchange.success")
                .description("商城礼品兑换成功总量")
                .register(registry);

        this.exchangeFailureCounter = Counter.builder("campus.mall.exchange.failure")
                .description("商城礼品兑换失败总量")
                .register(registry);

        this.exchangeDurationTimer = Timer.builder("campus.mall.exchange.duration")
                .description("商城礼品兑换执行耗时分布 (支持 P95/P99 监控)")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(10))
                .register(registry);

        // 初始化活动打卡指标
        this.checkinTotalCounter = Counter.builder("campus.activity.checkin.total")
                .description("活动现场打卡核验总量")
                .register(registry);

        this.checkinSuccessCounter = Counter.builder("campus.activity.checkin.success")
                .description("活动现场打卡核验成功总量")
                .register(registry);

        this.checkinFailureCounter = Counter.builder("campus.activity.checkin.failure")
                .description("活动现场打卡核验失败总量")
                .register(registry);

        this.checkinDurationTimer = Timer.builder("campus.activity.checkin.duration")
                .description("活动打卡流水线执行耗时分布")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(registry);

        // 初始化死信超时关单指标
        this.orderExpireCounter = Counter.builder("campus.mall.order.expire.total")
                .description("MQ 延时死信超时关单与积分回退总量")
                .register(registry);

        // 初始化发件箱判死与重试指标 (P1-D)
        this.outboxDeadCounter = Counter.builder("campus.mall.outbox.dead.total")
                .description("事务发件箱消息重试超限被判死（死信）总量")
                .register(registry);

        this.outboxRetryCounter = Counter.builder("campus.mall.outbox.retry.total")
                .description("事务发件箱消息补偿投递重试总量")
                .register(registry);
    }

    /**
     * 记录一笔礼品兑换操作
     */
    public void recordExchange(boolean success, long durationMs) {
        exchangeTotalCounter.increment();
        if (success) {
            exchangeSuccessCounter.increment();
        } else {
            exchangeFailureCounter.increment();
        }
        if (durationMs > 0) {
            exchangeDurationTimer.record(durationMs, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 记录一次活动打卡核验
     */
    public void recordCheckin(boolean success, long durationMs) {
        checkinTotalCounter.increment();
        if (success) {
            checkinSuccessCounter.increment();
        } else {
            checkinFailureCounter.increment();
        }
        if (durationMs > 0) {
            checkinDurationTimer.record(durationMs, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 记录一次延时超时关单
     */
    public void recordOrderExpired() {
        orderExpireCounter.increment();
    }

    /**
     * 记录一次发件箱消息判死 (P1-D)
     */
    public void recordOutboxDead() {
        outboxDeadCounter.increment();
    }

    /**
     * 记录一次发件箱消息补偿重试 (P1-D)
     */
    public void recordOutboxRetry() {
        outboxRetryCounter.increment();
    }

    public MeterRegistry getRegistry() {
        return registry;
    }
}
