package com.campus.mq;

import com.campus.modules.activity.service.ActivityCacheService;
import com.campus.modules.mall.service.StudentGiftService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * MQ 消费者 ACK 策略回归测试（2026-10-29，P0-2）。
 *
 * <p>背景：全局原先配置 {@code acknowledge-mode: manual}，而 {@link CampusMessageConsumer} 的
 * 三个监听器既不接收 {@code Channel} 也不调用 {@code basicAck}，导致消息永远停在 Unacked；
 * 每个队列上限 {@code prefetch × concurrency}（10 × 4 = 40），攒满即整条队列停止投递，
 * 且每次重启都把历史消息全量重放。实测 60 条消息只消费 40 条、剩下 20 条永久停在 ready。
 *
 * <p>本测试锁住两条不变量：
 * <ol>
 *   <li><b>每个 {@code @RabbitListener} 都必须显式声明 {@code ackMode}</b>——禁止再依赖全局默认值，
 *       因为默认值一旦是 manual，漏写 ack 就是静默堆积。</li>
 *   <li><b>AUTO 模式的监听器不得把异常抛给容器</b>——AUTO 下抛异常等于 nack + 立即重投，
 *       一条毒消息会在 prefetch 窗口内打成热循环；旁路副作用消息应由业务代码记录后放弃。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class CampusMessageConsumerTest {

    /** 当前全部监听器所在类；新增监听器时同步维护，配合下面的数量断言防止漏改 */
    private static final Class<?>[] LISTENER_CLASSES = {
            CampusMessageConsumer.class,
            OrderCreateConsumer.class
    };

    @Mock
    private ActivityCacheService activityCacheService;

    @Mock
    private StudentGiftService studentGiftService;

    @InjectMocks
    private CampusMessageConsumer consumer;

    // ==================== 1. ACK 模式的静态契约 ====================

    @Test
    @DisplayName("ACK 策略：所有 @RabbitListener 必须显式声明 ackMode，禁止依赖全局默认值")
    void everyRabbitListenerMustDeclareAckModeExplicitly() {
        int listenerCount = 0;
        for (Class<?> clazz : LISTENER_CLASSES) {
            for (Method method : clazz.getDeclaredMethods()) {
                RabbitListener annotation = method.getAnnotation(RabbitListener.class);
                if (annotation == null) {
                    continue;
                }
                listenerCount++;
                assertFalse(annotation.ackMode() == null || annotation.ackMode().isBlank(),
                        clazz.getSimpleName() + "#" + method.getName()
                                + " 未声明 ackMode；全局默认一旦变回 manual，该队列会静默堆积 Unacked 直至停止投递");
            }
        }
        assertEquals(4, listenerCount,
                "监听器数量变了：新增/删除监听器时请同步更新本断言与 LISTENER_CLASSES");
    }

    @Test
    @DisplayName("ACK 策略：三个旁路队列用 AUTO，方法返回即确认")
    void sideEffectQueuesUseAutoAck() {
        assertAckMode(CampusMessageConsumer.class, "refreshActivityCache", "AUTO");
        assertAckMode(CampusMessageConsumer.class, "handleCheckinEvent", "AUTO");
        assertAckMode(CampusMessageConsumer.class, "handleGiftOrderExpire", "AUTO");
    }

    @Test
    @DisplayName("ACK 策略：资金链路必须保持 MANUAL，订单事务提交后才显式 ack")
    void orderCreateConsumerKeepsManualAck() {
        assertAckMode(OrderCreateConsumer.class, "handleOrderCreate", "MANUAL");
    }

    @Test
    @DisplayName("ACK 策略：AUTO 监听器不得再声明 Channel 参数（避免半自动半手动的混用）")
    void autoAckListenersMustNotDeclareChannel() {
        for (Method method : CampusMessageConsumer.class.getDeclaredMethods()) {
            RabbitListener annotation = method.getAnnotation(RabbitListener.class);
            if (annotation == null) {
                continue;
            }
            for (Parameter parameter : method.getParameters()) {
                assertFalse("com.rabbitmq.client.Channel".equals(parameter.getType().getName()),
                        method.getName() + " 声明了 Channel 参数却使用 AUTO 模式："
                                + "AUTO 由容器确认，手动 basicAck 会造成重复确认导致 channel 异常");
            }
        }
    }

    // ==================== 2. 失败路径不得把异常抛给容器 ====================

    @Test
    @DisplayName("延时关单：expireOrder 抛异常时不向外抛（否则 AUTO 模式会无限重投打成热循环）")
    void giftExpireSwallowsFailureSoAutoModeCanAck() {
        doThrow(new IllegalStateException("db down")).when(studentGiftService).expireOrder(999L);

        assertDoesNotThrow(() -> consumer.handleGiftOrderExpire(
                        Map.of("orderId", 999L, "pickupCode", "PK20261029"), "trace-expire"),
                "监听器必须吞掉业务异常：订单的最终一致性由 GiftOrderTimeoutScheduler 兜底");

        verify(studentGiftService).expireOrder(999L);
        assertNull(MDC.get("traceId"), "监听器返回前必须清理 MDC，避免线程池复用串号");
    }

    @Test
    @DisplayName("延时关单：空消息体/非法 orderId 都不抛异常且不触发关单")
    void giftExpireHandlesMalformedPayload() {
        assertDoesNotThrow(() -> consumer.handleGiftOrderExpire(null, null));
        assertDoesNotThrow(() -> consumer.handleGiftOrderExpire(Map.of("orderId", "not-a-number"), null));

        verify(studentGiftService, never()).expireOrder(anyLong());
    }

    @Test
    @DisplayName("延时关单：正常消息体仍然照常驱动 expireOrder")
    void giftExpireStillClosesOrderOnHappyPath() {
        assertDoesNotThrow(() -> consumer.handleGiftOrderExpire(Map.of("orderId", 148L), null));

        verify(studentGiftService).expireOrder(148L);
    }

    @Test
    @DisplayName("活动缓存刷新：缓存服务抛异常时吞掉，读路径会回源自愈")
    void activityCacheRefreshSwallowsFailure() {
        doThrow(new IllegalStateException("redis down")).when(activityCacheService).refresh(1L);

        assertDoesNotThrow(() -> consumer.refreshActivityCache(Map.of("activityId", 1L), "trace-cache"));
        assertDoesNotThrow(() -> consumer.refreshActivityCache(null, null));

        verify(activityCacheService).refresh(1L);
    }

    @Test
    @DisplayName("打卡事件：只写审计日志，空消息体与正常消息都不抛异常")
    void checkinEventNeverThrows() {
        assertDoesNotThrow(() -> consumer.handleCheckinEvent(Map.of("studentId", 1, "activityId", 2), "trace-checkin"));
        assertDoesNotThrow(() -> consumer.handleCheckinEvent(null, null));
    }

    // ==================== 工具方法 ====================

    private void assertAckMode(Class<?> clazz, String methodName, String expected) {
        Method method = Arrays.stream(clazz.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到监听器方法: " + clazz.getSimpleName() + "#" + methodName));
        RabbitListener annotation = method.getAnnotation(RabbitListener.class);
        assertNotNull(annotation, methodName + " 缺少 @RabbitListener");
        assertEquals(expected, annotation.ackMode().toUpperCase(Locale.ROOT),
                clazz.getSimpleName() + "#" + methodName + " 的 ackMode 必须为 " + expected);
        assertTrue(annotation.queues().length > 0, methodName + " 必须声明监听队列");
    }
}
