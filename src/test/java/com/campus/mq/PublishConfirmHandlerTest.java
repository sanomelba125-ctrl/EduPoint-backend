package com.campus.mq;

import com.campus.common.metrics.CampusMetricsCollector;
import com.campus.modules.mall.service.LocalMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 发布确认（Publisher Confirm / Returns）闭环单测。
 *
 * <p>覆盖三类结果：Broker ACK 才推进状态、NACK 退回重投、不可路由后紧跟的 ACK 不得误标已投递。
 */
@ExtendWith(MockitoExtension.class)
public class PublishConfirmHandlerTest {

    @Mock
    private LocalMessageService localMessageService;

    @Mock
    private CampusMetricsCollector metricsCollector;

    private PublishConfirmHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PublishConfirmHandler(localMessageService, metricsCollector);
    }

    @Test
    @DisplayName("Broker ACK：本地消息推进为已投递，且不回退")
    void ackShouldMarkPublished() {
        handler.confirm(new CorrelationData("msg-ack-1"), true, null);

        verify(localMessageService, times(1)).markPublished("msg-ack-1");
        verify(localMessageService, never()).resetToPending(anyString(), anyString(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Broker NACK：消息退回待重投并累加重试，绝不能标记为已投递")
    void nackShouldResetToPending() {
        when(localMessageService.resetToPending(anyString(), anyString(), any(LocalDateTime.class))).thenReturn(1);

        handler.confirm(new CorrelationData("msg-nack-1"), false, "queue full");

        verify(localMessageService, never()).markPublished(anyString());
        verify(localMessageService, times(1)).resetToPending(eq("msg-nack-1"), contains("NACK"), any(LocalDateTime.class));
        verify(metricsCollector, times(1)).recordOutboxRetry();
    }

    @Test
    @DisplayName("不可路由（basic.return）后紧跟的 ACK 不得把消息标成已投递，必须保持待重投")
    void returnedThenAckShouldNotMarkPublished() {
        when(localMessageService.resetToPending(anyString(), anyString(), any(LocalDateTime.class))).thenReturn(1);

        handler.returnedMessage(returnedMessage("msg-return-1"));
        // AMQP 协议保证 basic.return 先于 basic.ack 到达
        handler.confirm(new CorrelationData("msg-return-1"), true, null);

        verify(localMessageService, never()).markPublished("msg-return-1");
        verify(localMessageService, times(2)).resetToPending(eq("msg-return-1"), anyString(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("不可路由且状态回退未生效（消息已被消费）时，也不得改写终态")
    void returnedButAlreadyConsumedShouldBeNoop() {
        when(localMessageService.resetToPending(anyString(), anyString(), any(LocalDateTime.class))).thenReturn(0);

        handler.returnedMessage(returnedMessage("msg-return-2"));

        verify(localMessageService, never()).markPublished(anyString());
        // 回退行数为 0 说明消息已是终态：不重复上报重试指标，避免污染告警口径
        verify(metricsCollector, never()).recordOutboxRetry();
    }

    @Test
    @DisplayName("缺少关联ID的消息（非发件箱投递）不触碰任何本地消息状态")
    void messageWithoutCorrelationShouldBeIgnored() {
        handler.confirm(null, true, null);
        handler.confirm(null, false, "unknown");

        verify(localMessageService, never()).markPublished(anyString());
        verify(localMessageService, never()).resetToPending(anyString(), anyString(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("return 回调缺少 messageId header 时不报错、不误改状态")
    void returnedWithoutHeaderShouldBeIgnored() {
        MessageProperties properties = new MessageProperties();
        Message message = new Message("{}".getBytes(StandardCharsets.UTF_8), properties);

        handler.returnedMessage(new ReturnedMessage(message, 312, "NO_ROUTE", "ex", "rk"));

        verify(localMessageService, never()).resetToPending(anyString(), anyString(), any(LocalDateTime.class));
    }

    /** 构造一条带 x-message-id header 的退回消息，模拟 Broker 的 basic.return。 */
    private ReturnedMessage returnedMessage(String messageId) {
        MessageProperties properties = new MessageProperties();
        properties.setHeader(PublishConfirmHandler.HEADER_MESSAGE_ID, messageId);
        Message message = new Message("{\"messageId\":\"x\"}".getBytes(StandardCharsets.UTF_8), properties);
        return new ReturnedMessage(message, 312, "NO_ROUTE", "campus.mall.order.create.exchange", "campus.mall.order.create.key");
    }
}
