package com.campus.modules.mall;

import com.campus.config.RabbitMqConfig;
import com.campus.modules.mall.entity.LocalMessage;
import com.campus.modules.mall.mapper.LocalMessageMapper;
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

    private LocalMessageService localMessageService;
    private OutboxRetryScheduler outboxRetryScheduler;

    @BeforeEach
    void setUp() {
        localMessageService = new LocalMessageService(localMessageMapper);
        outboxRetryScheduler = new OutboxRetryScheduler(localMessageService, rabbitTemplate);
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
}
