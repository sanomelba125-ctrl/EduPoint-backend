package com.campus.common;

import com.campus.common.metrics.CampusMetricsCollector;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("P3-1: Prometheus 自定义生产级业务指标采集测试")
class CampusMetricsTest {

    private MeterRegistry registry;
    private CampusMetricsCollector collector;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        collector = new CampusMetricsCollector(registry);
    }

    @Test
    @DisplayName("礼品兑换量、成功率与耗时 Timer 正确累加记录")
    void testExchangeMetrics() {
        collector.recordExchange(true, 120);
        collector.recordExchange(true, 80);
        collector.recordExchange(false, 30);

        Counter total = registry.find("campus.mall.exchange.total").counter();
        assertNotNull(total);
        assertEquals(3.0, total.count());

        Counter success = registry.find("campus.mall.exchange.success").counter();
        assertNotNull(success);
        assertEquals(2.0, success.count());

        Counter failure = registry.find("campus.mall.exchange.failure").counter();
        assertNotNull(failure);
        assertEquals(1.0, failure.count());

        Timer timer = registry.find("campus.mall.exchange.duration").timer();
        assertNotNull(timer);
        assertEquals(3, timer.count());
        assertTrue(timer.totalTime(TimeUnit.MILLISECONDS) >= 230);
    }

    @Test
    @DisplayName("活动打卡现场核验指标累加与耗时分布")
    void testCheckinMetrics() {
        collector.recordCheckin(true, 45);
        collector.recordCheckin(false, 15);

        Counter total = registry.find("campus.activity.checkin.total").counter();
        assertNotNull(total);
        assertEquals(2.0, total.count());

        Counter success = registry.find("campus.activity.checkin.success").counter();
        assertNotNull(success);
        assertEquals(1.0, success.count());

        Counter failure = registry.find("campus.activity.checkin.failure").counter();
        assertNotNull(failure);
        assertEquals(1.0, failure.count());

        Timer timer = registry.find("campus.activity.checkin.duration").timer();
        assertNotNull(timer);
        assertEquals(2, timer.count());
    }

    @Test
    @DisplayName("延时死信超时关单计数器正确累加")
    void testOrderExpireMetrics() {
        collector.recordOrderExpired();
        collector.recordOrderExpired();

        Counter expire = registry.find("campus.mall.order.expire.total").counter();
        assertNotNull(expire);
        assertEquals(2.0, expire.count());
    }
}
