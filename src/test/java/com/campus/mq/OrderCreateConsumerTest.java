package com.campus.mq;

import com.alibaba.fastjson2.JSON;
import com.campus.modules.mall.dto.OrderCreateMessage;
import com.campus.modules.mall.mapper.MqConsumeLogMapper;
import com.campus.modules.mall.service.GiftStockRedisService;
import com.campus.modules.mall.service.LocalMessageService;
import com.campus.modules.mall.service.StudentGiftService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 消费者幂等、手动 ACK、死信路由与库存补偿单测 (P1-2 & P1-4)
 */
@ExtendWith(MockitoExtension.class)
public class OrderCreateConsumerTest {

    @Mock
    private StudentGiftService studentGiftService;
    @Mock
    private LocalMessageService localMessageService;
    @Mock
    private GiftStockRedisService giftStockRedisService;
    @Mock
    private MqConsumeLogMapper mqConsumeLogMapper;
    @Mock
    private Channel channel;

    private OrderCreateConsumer consumer;

    private final long deliveryTag = 1001L;

    @BeforeEach
    void setUp() {
        consumer = new OrderCreateConsumer(
                studentGiftService,
                localMessageService,
                giftStockRedisService,
                mqConsumeLogMapper
        );
    }

    private Message createAmqpMessage(String body) {
        MessageProperties properties = new MessageProperties();
        properties.setHeader("x-trace-id", "trace-test-consumer");
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }

    private OrderCreateMessage sampleMsg() {
        return new OrderCreateMessage(
                "msg-uuid-999",
                1001L,
                6L,
                2,
                "req-idempotent-001",
                "pickup-token-001",
                "张三",
                "13800000000",
                "海棠宿舍1号楼",
                System.currentTimeMillis(),
                "trace-test-consumer"
        );
    }

    @Test
    @DisplayName("P1-4: 正常消费流程 - 订单落库成功 -> 记录消费幂等表 -> 本地消息标记完成 -> 手动 ACK 确认")
    void testHandleOrderCreate_Success() throws IOException {
        OrderCreateMessage msg = sampleMsg();
        Message amqpMessage = createAmqpMessage(JSON.toJSONString(msg));

        when(mqConsumeLogMapper.existsConsumed(eq(msg.messageId()), anyString())).thenReturn(0);
        when(studentGiftService.processAsyncOrder(any(OrderCreateMessage.class))).thenReturn(true);

        consumer.handleOrderCreate(amqpMessage, channel, deliveryTag);

        verify(studentGiftService, times(1)).processAsyncOrder(any(OrderCreateMessage.class));
        verify(mqConsumeLogMapper, times(1)).insertConsumeLog(eq(msg.messageId()), eq("OrderCreateConsumer"), eq("GIFT_ORDER_CREATE"), eq(msg.requestId()), eq(1));
        verify(localMessageService, times(1)).markConsumed(eq(msg.messageId()));
        verify(channel, times(1)).basicAck(eq(deliveryTag), eq(false));
    }

    @Test
    @DisplayName("P1-4: 消费幂等拦截 - 已处理过的消息直接 ACK 确认，绝不重复创建订单")
    void testHandleOrderCreate_DuplicateMessage_DirectAck() throws IOException {
        OrderCreateMessage msg = sampleMsg();
        Message amqpMessage = createAmqpMessage(JSON.toJSONString(msg));

        // 模拟已存在消费记录
        when(mqConsumeLogMapper.existsConsumed(eq(msg.messageId()), anyString())).thenReturn(1);

        consumer.handleOrderCreate(amqpMessage, channel, deliveryTag);

        // 确认不会再调用订单创建
        verify(studentGiftService, never()).processAsyncOrder(any());
        // 直接 ACK，防止阻塞后续消息
        verify(channel, times(1)).basicAck(eq(deliveryTag), eq(false));
    }

    @Test
    @DisplayName("P1-5: 业务校验拒绝 - 异步落库因账户余额不足拒绝 -> 反向回补 Redis 库存 -> 标记失败 -> ACK 确认")
    void testHandleOrderCreate_BusinessFailure_StockCompensated() throws IOException {
        OrderCreateMessage msg = sampleMsg();
        Message amqpMessage = createAmqpMessage(JSON.toJSONString(msg));

        when(mqConsumeLogMapper.existsConsumed(eq(msg.messageId()), anyString())).thenReturn(0);
        when(studentGiftService.processAsyncOrder(any(OrderCreateMessage.class))).thenReturn(false);

        consumer.handleOrderCreate(amqpMessage, channel, deliveryTag);

        // 验证反向回补 Redis 库存
        verify(giftStockRedisService, times(1)).compensateStock(eq(msg.giftId()), eq(msg.quantity()));
        verify(localMessageService, times(1)).markFailed(eq(msg.messageId()), eq("业务校验未通过"));
        verify(channel, times(1)).basicAck(eq(deliveryTag), eq(false));
    }

    @Test
    @DisplayName("P1-4: 格式损坏毒消息 - 反序列化失败拒绝重新入队 -> 路由至死信队列 (DLX)")
    void testHandleOrderCreate_CorruptedPayload_RoutedToDLX() throws IOException {
        Message amqpMessage = createAmqpMessage("bad-json-syntax{{{");

        consumer.handleOrderCreate(amqpMessage, channel, deliveryTag);

        // requeue = false -> 自动由 RabbitMQ 转入死信队列
        verify(channel, times(1)).basicNack(eq(deliveryTag), eq(false), eq(false));
        verify(studentGiftService, never()).processAsyncOrder(any());
    }

    @Test
    @DisplayName("P1-4 & P1-5: 未捕获系统异常 - 触发反向库存补偿 -> 拒绝重新入队进入死信队列")
    void testHandleOrderCreate_FatalException_CompensateAndNack() throws IOException {
        OrderCreateMessage msg = sampleMsg();
        Message amqpMessage = createAmqpMessage(JSON.toJSONString(msg));

        when(mqConsumeLogMapper.existsConsumed(eq(msg.messageId()), anyString())).thenReturn(0);
        when(studentGiftService.processAsyncOrder(any(OrderCreateMessage.class)))
                .thenThrow(new RuntimeException("Database connection timeout"));

        consumer.handleOrderCreate(amqpMessage, channel, deliveryTag);

        // 验证库存反向补偿，避免少卖
        verify(giftStockRedisService, times(1)).compensateStock(eq(msg.giftId()), eq(msg.quantity()));
        // 拒绝并进入死信队列
        verify(channel, times(1)).basicNack(eq(deliveryTag), eq(false), eq(false));
    }
}
