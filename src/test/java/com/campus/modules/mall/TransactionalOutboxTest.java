package com.campus.modules.mall;

import com.alibaba.fastjson2.JSON;
import com.campus.common.metrics.CampusMetricsCollector;
import com.campus.config.RabbitMqConfig;
import com.campus.modules.mall.dto.OrderCreateMessage;
import com.campus.modules.mall.entity.LocalMessage;
import com.campus.modules.mall.mapper.LocalMessageMapper;
import com.campus.modules.mall.service.GiftStockRedisService;
import com.campus.modules.mall.task.OutboxRetryScheduler;
import com.campus.modules.mall.service.LocalMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 事务性发件箱 (Transactional Outbox) 机制与重试补偿单测 (P1-3)
 */
@ExtendWith(MockitoExtension.class)
public class TransactionalOutboxTest {

    @Mock
    private LocalMessageMapper localMessageMapper;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private GiftStockRedisService giftStockRedisService;

    @Mock
    private CampusMetricsCollector metricsCollector;

    private LocalMessageService localMessageService;
    private OutboxRetryScheduler outboxRetryScheduler;

    @BeforeEach
    void setUp() {
        localMessageService = new LocalMessageService(localMessageMapper);
        outboxRetryScheduler = new OutboxRetryScheduler(
                localMessageService, rabbitTemplate, giftStockRedisService, metricsCollector);
    }

    @Test
    @DisplayName("P1-3: 本地消息落库 - 待发送状态与指数退避初值正确初始化")
    void testSavePendingMessage() {
        String messageId = "msg-uuid-001";
        String bizType = "GIFT_ORDER_CREATE";
        String bizId = "ORD20260923001";
        Map<String, Object> payload = Map.of("giftId", 1L, "quantity", 2);
        String traceId = "trace-test-123";

        localMessageService.savePendingMessage(messageId, bizType, bizId, payload, traceId);

        ArgumentCaptor<LocalMessage> captor = ArgumentCaptor.forClass(LocalMessage.class);
        verify(localMessageMapper, times(1)).insert(captor.capture());

        LocalMessage saved = captor.getValue();
        assertEquals(messageId, saved.getMessageId());
        assertEquals(bizType, saved.getBizType());
        assertEquals(bizId, saved.getBizId());
        assertEquals(Integer.valueOf(0), saved.getStatus()); // 0-待发送
        assertEquals(Integer.valueOf(0), saved.getRetryCount());
        assertEquals(traceId, saved.getTraceId());
        assertNotNull(saved.getNextRetryTime());
        assertTrue(saved.getPayload().contains("\"giftId\":1"));
    }

    @Test
    @DisplayName("P1-3: 状态流转 - 成功投递 Broker 后状态推进为 PUBLISHED")
    void testMarkPublished() {
        String messageId = "msg-uuid-002";
        localMessageService.markPublished(messageId);
        verify(localMessageMapper, times(1)).markAsPublished(messageId);
    }

    @Test
    @DisplayName("P1-3: 状态流转 - 消费成功后状态推进为 CONSUMED")
    void testMarkConsumed() {
        String messageId = "msg-uuid-003";
        localMessageService.markConsumed(messageId);
        verify(localMessageMapper, times(1)).markAsConsumed(messageId);
    }

    @Test
    @DisplayName("P1-3: 状态流转 - 超过最大重试次数后状态推进为 FAILED 并记录根因")
    void testMarkFailed() {
        String messageId = "msg-uuid-004";
        String reason = "RabbitMQ broker unavailable after 5 retries";
        localMessageService.markFailed(messageId, reason);
        verify(localMessageMapper, times(1)).markAsFailed(messageId, 3, reason);
    }

    @Test
    @DisplayName("P1-3: 指数退避算法 - 随着重试次数递增，退避时间倍增")
    void testIncrementRetry_ExponentialBackoff() {
        String messageId = "msg-uuid-005";
        int currentRetry = 2; // 第2次重试，预期退避 2^2 * 5 = 20 秒
        String reason = "Connection reset";

        ArgumentCaptor<LocalDateTime> nextTimeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        localMessageService.incrementRetry(messageId, currentRetry, reason);

        verify(localMessageMapper, times(1)).incrementRetry(eq(messageId), nextTimeCaptor.capture(), eq(reason));

        LocalDateTime nextRetry = nextTimeCaptor.getValue();
        LocalDateTime expectedMin = LocalDateTime.now().plusSeconds(18);
        LocalDateTime expectedMax = LocalDateTime.now().plusSeconds(25);
        assertTrue(nextRetry.isAfter(expectedMin) && nextRetry.isBefore(expectedMax));
    }

    @Test
    @DisplayName("P1-3: 定时任务自动扫描超时未投递消息并补发 MQ")
    void testOutboxRetryScheduler_ScanAndResend() {
        LocalMessage pendingMsg = new LocalMessage();
        pendingMsg.setMessageId("msg-retry-001");
        pendingMsg.setBizType("GIFT_ORDER_CREATE");
        pendingMsg.setPayload("{\"giftId\":1,\"studentId\":1001}");
        pendingMsg.setRetryCount(1);
        pendingMsg.setStatus(0);

        when(localMessageMapper.selectPendingMessages(any(LocalDateTime.class), eq(5), eq(50)))
                .thenReturn(List.of(pendingMsg));

        outboxRetryScheduler.retryPendingMessages();

        // 验证重新发送到交换机
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMqConfig.SECKILL_ORDER_EXCHANGE),
                eq(RabbitMqConfig.SECKILL_ORDER_ROUTING_KEY),
                eq(pendingMsg.getPayload())
        );
        // 验证投递成功后更新状态为已发送
        verify(localMessageMapper, times(1)).markAsPublished(pendingMsg.getMessageId());
    }

    @Test
    @DisplayName("P1-5: 在途预扣统计 - 按礼品聚合未完成订单件数，损坏/缺字段载荷跳过而不干扰统计")
    void testCountInflightOrderQtyByGift() {
        LocalMessage first = inflightMessage("msg-inflight-1", 1L, 2);
        LocalMessage second = inflightMessage("msg-inflight-2", 1L, 1);
        LocalMessage third = inflightMessage("msg-inflight-3", 2L, 3);

        LocalMessage broken = new LocalMessage();
        broken.setMessageId("msg-broken");
        broken.setPayload("{ 这不是合法 JSON");

        LocalMessage withoutGiftId = new LocalMessage();
        withoutGiftId.setMessageId("msg-without-gift");
        withoutGiftId.setPayload("{\"messageId\":\"x\",\"studentId\":1001}");

        when(localMessageMapper.selectInflightOrderMessages())
                .thenReturn(List.of(first, second, third, broken, withoutGiftId));

        Map<Long, Integer> inflightByGift = localMessageService.countInflightOrderQtyByGift();

        assertEquals(2, inflightByGift.size());
        assertEquals(Integer.valueOf(3), inflightByGift.get(1L)); // 2 + 1
        assertEquals(Integer.valueOf(3), inflightByGift.get(2L));
    }

    @Test
    @DisplayName("P1-5: 在途预扣统计 - 无未完成订单时返回空快照（对账退化为按实存比对）")
    void testCountInflightOrderQtyByGift_Empty() {
        when(localMessageMapper.selectInflightOrderMessages()).thenReturn(List.of());

        assertTrue(localMessageService.countInflightOrderQtyByGift().isEmpty());
    }

    @Test
    @DisplayName("P1-5: 消息判死（重试超限）时回补 Redis 预扣，避免该笔库存被永久占用造成少卖")
    void testOutboxRetryScheduler_DeadMessage_CompensatesStock() {
        LocalMessage deadMessage = inflightMessage("msg-dead-001", 7L, 2);
        deadMessage.setRetryCount(5); // 达到 MAX_RETRIES，判定为死亡
        deadMessage.setStatus(0);

        when(localMessageMapper.selectPendingMessages(any(LocalDateTime.class), eq(5), eq(50)))
                .thenReturn(List.of(deadMessage));

        outboxRetryScheduler.retryPendingMessages();

        // 判死的消息不再重投：重投成功会把状态推进为 PUBLISHED，此处必须不发生
        verify(localMessageMapper, never()).markAsPublished(anyString());
        // 但必须回补它在秒杀入口预扣的 Redis 库存
        verify(giftStockRedisService, times(1)).compensateStock(7L, 2);
        verify(localMessageMapper, times(1)).markAsFailed(eq("msg-dead-001"), eq(3), anyString());
        // 且必须上报 Outbox 判死度量指标
        verify(metricsCollector, times(1)).recordOutboxDead();
    }

    @Test
    @DisplayName("P1-D: 消息投递抛异常时执行重试并上报重试指标")
    void testOutboxRetryScheduler_RetryFailure_RecordsMetric() {
        LocalMessage pendingMsg = inflightMessage("msg-retry-fail-001", 7L, 1);
        pendingMsg.setRetryCount(1);
        pendingMsg.setStatus(0);

        when(localMessageMapper.selectPendingMessages(any(LocalDateTime.class), eq(5), eq(50)))
                .thenReturn(List.of(pendingMsg));
        doThrow(new RuntimeException("RabbitMQ connection timed out"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        outboxRetryScheduler.retryPendingMessages();

        verify(localMessageMapper, times(1)).incrementRetry(eq("msg-retry-fail-001"), any(LocalDateTime.class), anyString());
        verify(metricsCollector, times(1)).recordOutboxRetry();
    }

    /** 构造一条「在途」发件箱消息：payload 与生产同源，均为 OrderCreateMessage 契约序列化结果 */
    private LocalMessage inflightMessage(String messageId, Long giftId, int quantity) {
        OrderCreateMessage payload = new OrderCreateMessage(
                messageId, 1001L, giftId, quantity, "req-" + messageId, null,
                "张三", "13911110006", "大学生活动中心一楼 102 创客服务台",
                System.currentTimeMillis(), "trace-test", "ORD-TEST-0001");
        LocalMessage message = new LocalMessage();
        message.setMessageId(messageId);
        message.setBizType("GIFT_ORDER_CREATE");
        message.setPayload(JSON.toJSONString(payload));
        message.setStatus(0);
        return message;
    }
}
