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
        // 重投时也要补齐本地消息完成态，避免 Outbox 永久停在 status=1
        verify(localMessageService, times(1)).markConsumed(eq(msg.messageId()));
        // 直接 ACK，防止阻塞后续消息
        verify(channel, times(1)).basicAck(eq(deliveryTag), eq(false));
    }

    @Test
    @DisplayName("P0-4: 订单已提交但 markConsumed 失败 - 不得回补库存")
    void testHandleOrderCreate_SuccessButMarkConsumedFails_NoCompensation() throws IOException {
        OrderCreateMessage msg = sampleMsg();
        Message amqpMessage = createAmqpMessage(JSON.toJSONString(msg));

        when(mqConsumeLogMapper.existsConsumed(eq(msg.messageId()), anyString())).thenReturn(0);
        when(studentGiftService.processAsyncOrder(any(OrderCreateMessage.class))).thenReturn(true);
        doThrow(new RuntimeException("local_message update failed"))
                .when(localMessageService).markConsumed(eq(msg.messageId()));

        consumer.handleOrderCreate(amqpMessage, channel, deliveryTag);

        verify(giftStockRedisService, never()).compensateStockOnce(anyLong(), anyInt(), anyString());
        verify(giftStockRedisService, never()).compensateStock(anyLong(), anyInt());
        verify(channel, times(1)).basicAck(eq(deliveryTag), eq(false));
    }

    @Test
    @DisplayName("P0-4: 订单已提交但 ACK 失败 - 不得回补库存")
    void testHandleOrderCreate_SuccessButAckFails_NoCompensation() throws IOException {
        OrderCreateMessage msg = sampleMsg();
        Message amqpMessage = createAmqpMessage(JSON.toJSONString(msg));

        when(mqConsumeLogMapper.existsConsumed(eq(msg.messageId()), anyString())).thenReturn(0);
        when(studentGiftService.processAsyncOrder(any(OrderCreateMessage.class))).thenReturn(true);
        doThrow(new IOException("channel closed")).when(channel).basicAck(eq(deliveryTag), eq(false));

        consumer.handleOrderCreate(amqpMessage, channel, deliveryTag);

        verify(giftStockRedisService, never()).compensateStockOnce(anyLong(), anyInt(), anyString());
        verify(giftStockRedisService, never()).compensateStock(anyLong(), anyInt());
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("P0-4: 事务抛异常但订单实际已落库 - 按成功收尾，不回补库存")
    void testHandleOrderCreate_ExceptionButOrderPersisted_NoCompensation() throws IOException {
        OrderCreateMessage msg = sampleMsg();
        Message amqpMessage = createAmqpMessage(JSON.toJSONString(msg));

        when(mqConsumeLogMapper.existsConsumed(eq(msg.messageId()), anyString())).thenReturn(0);
        when(studentGiftService.processAsyncOrder(any(OrderCreateMessage.class)))
                .thenThrow(new RuntimeException("commit response lost"));
        when(studentGiftService.existsOrderByRequestId(eq(msg.studentId()), eq(msg.requestId()))).thenReturn(true);

        consumer.handleOrderCreate(amqpMessage, channel, deliveryTag);

        verify(giftStockRedisService, never()).compensateStockOnce(anyLong(), anyInt(), anyString());
        verify(localMessageService, times(1)).markConsumed(eq(msg.messageId()));
        verify(channel, times(1)).basicAck(eq(deliveryTag), eq(false));
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("P0-4: 事务抛异常且无法确认订单状态 - 转死信，不盲目补偿")
    void testHandleOrderCreate_ExceptionAndVerifyFails_NoCompensation() throws IOException {
        OrderCreateMessage msg = sampleMsg();
        Message amqpMessage = createAmqpMessage(JSON.toJSONString(msg));

        when(mqConsumeLogMapper.existsConsumed(eq(msg.messageId()), anyString())).thenReturn(0);
        when(studentGiftService.processAsyncOrder(any(OrderCreateMessage.class)))
                .thenThrow(new RuntimeException("db commit failed"));
        when(studentGiftService.existsOrderByRequestId(eq(msg.studentId()), eq(msg.requestId())))
                .thenThrow(new RuntimeException("db unavailable"));

        consumer.handleOrderCreate(amqpMessage, channel, deliveryTag);

        verify(giftStockRedisService, never()).compensateStockOnce(anyLong(), anyInt(), anyString());
        verify(giftStockRedisService, never()).compensateStock(anyLong(), anyInt());
        verify(channel, times(1)).basicNack(eq(deliveryTag), eq(false), eq(false));
    }

    @Test
    @DisplayName("P1-5: 业务校验拒绝 - 异步落库因账户余额不足拒绝 -> 反向回补 Redis 库存 -> 标记失败 -> ACK 确认")
    void testHandleOrderCreate_BusinessFailure_StockCompensated() throws IOException {
        OrderCreateMessage msg = sampleMsg();
        Message amqpMessage = createAmqpMessage(JSON.toJSONString(msg));

        when(mqConsumeLogMapper.existsConsumed(eq(msg.messageId()), anyString())).thenReturn(0);
        when(studentGiftService.processAsyncOrder(any(OrderCreateMessage.class))).thenReturn(false);

        consumer.handleOrderCreate(amqpMessage, channel, deliveryTag);

        // 验证反向回补 Redis 库存（按 messageId 幂等）
        verify(giftStockRedisService, times(1)).compensateStockOnce(eq(msg.giftId()), eq(msg.quantity()), eq(msg.messageId()));
        verify(giftStockRedisService, never()).compensateStock(anyLong(), anyInt());
        verify(localMessageService, times(1)).markFailed(eq(msg.messageId()), eq("业务校验未通过"));
        verify(channel, times(1)).basicAck(eq(deliveryTag), eq(false));
    }

    @Test
    @DisplayName("P0-2: Jackson 二次编码的 JSON 字符串 - 自动解包后正常消费，不得误投死信")
    void testHandleOrderCreate_DoubleEncodedJson_UnwrappedAndConsumed() throws IOException {
        OrderCreateMessage msg = sampleMsg();
        String singleEncoded = JSON.toJSONString(msg);
        Message amqpMessage = createAmqpMessage(JSON.toJSONString(singleEncoded));

        when(mqConsumeLogMapper.existsConsumed(eq(msg.messageId()), anyString())).thenReturn(0);
        when(studentGiftService.processAsyncOrder(any(OrderCreateMessage.class))).thenReturn(true);

        consumer.handleOrderCreate(amqpMessage, channel, deliveryTag);

        verify(studentGiftService, times(1)).processAsyncOrder(any(OrderCreateMessage.class));
        verify(channel, times(1)).basicAck(eq(deliveryTag), eq(false));
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("P0-4: 业务拒绝后 ACK 失败 - 库存仍只补偿一次")
    void testHandleOrderCreate_BusinessFailureAndAckFails_CompensatesOnce() throws IOException {
        OrderCreateMessage msg = sampleMsg();
        Message amqpMessage = createAmqpMessage(JSON.toJSONString(msg));

        when(mqConsumeLogMapper.existsConsumed(eq(msg.messageId()), anyString())).thenReturn(0);
        when(studentGiftService.processAsyncOrder(any(OrderCreateMessage.class))).thenReturn(false);
        doThrow(new IOException("channel closed")).when(channel).basicAck(eq(deliveryTag), eq(false));

        consumer.handleOrderCreate(amqpMessage, channel, deliveryTag);

        verify(giftStockRedisService, times(1)).compensateStockOnce(eq(msg.giftId()), eq(msg.quantity()), eq(msg.messageId()));
        verify(giftStockRedisService, never()).compensateStock(anyLong(), anyInt());
        verify(localMessageService, times(1)).markFailed(eq(msg.messageId()), eq("业务校验未通过"));
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
    @DisplayName("P1-4 & P1-5: 未捕获系统异常 - 触发反向库存补偿并记录失败终态 -> 拒绝重新入队进入死信队列")
    void testHandleOrderCreate_FatalException_CompensateAndNack() throws IOException {
        OrderCreateMessage msg = sampleMsg();
        Message amqpMessage = createAmqpMessage(JSON.toJSONString(msg));

        when(mqConsumeLogMapper.existsConsumed(eq(msg.messageId()), anyString())).thenReturn(0);
        when(studentGiftService.processAsyncOrder(any(OrderCreateMessage.class)))
                .thenThrow(new RuntimeException("Database connection timeout"));
        when(studentGiftService.existsOrderByRequestId(eq(msg.studentId()), eq(msg.requestId()))).thenReturn(false);

        consumer.handleOrderCreate(amqpMessage, channel, deliveryTag);

        // 验证库存按 messageId 幂等反向补偿，避免少卖
        verify(giftStockRedisService, times(1)).compensateStockOnce(eq(msg.giftId()), eq(msg.quantity()), eq(msg.messageId()));
        verify(giftStockRedisService, never()).compensateStock(anyLong(), anyInt());
        // 验证补齐消费失败日志与发件箱失败标记，避免 Outbox 兜底任务 10 分钟后盲目重投
        verify(mqConsumeLogMapper, times(1)).insertConsumeLog(
                eq(msg.messageId()), eq("OrderCreateConsumer"), eq("GIFT_ORDER_CREATE"), eq(msg.requestId()), eq(2));
        verify(localMessageService, times(1)).markFailed(eq(msg.messageId()), eq("订单事务未提交"));
        // 验证写入秒杀失败标记，避免客户端无限轮询
        verify(studentGiftService, times(1)).markSeckillFailed(eq(msg.studentId()), eq(msg.requestId()), anyString());
        // 拒绝并进入死信队列
        verify(channel, times(1)).basicNack(eq(deliveryTag), eq(false), eq(false));
    }

    @Test
    @DisplayName("P1-5: 并发重复消费业务拒绝时复核订单已落库 - 禁止二次回补库存，清理可能残留的失败标记，按成功收尾并 ACK")
    void testHandleOrderCreate_ConcurrentDuplicate_ProcessReturnsFalse_OrderExists_NoCompensation() throws IOException {
        OrderCreateMessage msg = sampleMsg();
        Message amqpMessage = createAmqpMessage(JSON.toJSONString(msg));

        when(mqConsumeLogMapper.existsConsumed(eq(msg.messageId()), anyString())).thenReturn(0);
        // 并发场景：本消费者执行异步落库时因并发竞争返回 false
        when(studentGiftService.processAsyncOrder(any(OrderCreateMessage.class))).thenReturn(false);
        // 但复核时发现同一 requestId 的订单已经被前一个并发消费者成功落库
        when(studentGiftService.existsOrderByRequestId(eq(msg.studentId()), eq(msg.requestId()))).thenReturn(true);

        consumer.handleOrderCreate(amqpMessage, channel, deliveryTag);

        // 绝不得反向回补 Redis 库存，避免超卖
        verify(giftStockRedisService, never()).compensateStockOnce(anyLong(), anyInt(), anyString());
        verify(giftStockRedisService, never()).compensateStock(anyLong(), anyInt());
        // 应清理可能残留的失败标记，避免轮询接口或诊断接口残留错误失败原因
        verify(studentGiftService, times(1)).clearSeckillFailure(eq(msg.studentId()), eq(msg.requestId()));
        // 应按成功收尾写入消费成功日志 (status=1) 并标记本地发件箱完成
        verify(mqConsumeLogMapper, times(1)).insertConsumeLog(
                eq(msg.messageId()), eq("OrderCreateConsumer"), eq("GIFT_ORDER_CREATE"), eq(msg.requestId()), eq(1));
        verify(localMessageService, times(1)).markConsumed(eq(msg.messageId()));
        // 不得标记为业务失败
        verify(localMessageService, never()).markFailed(anyString(), anyString());
        // 正常 ACK 确认
        verify(channel, times(1)).basicAck(eq(deliveryTag), eq(false));
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("P1-5: 异步落库返回 false 但复核订单状态抛异常 - 不盲目补偿库存，转死信等待核查")
    void testHandleOrderCreate_ProcessReturnsFalse_VerifyThrowsException_NoCompensationAndNack() throws IOException {
        OrderCreateMessage msg = sampleMsg();
        Message amqpMessage = createAmqpMessage(JSON.toJSONString(msg));

        when(mqConsumeLogMapper.existsConsumed(eq(msg.messageId()), anyString())).thenReturn(0);
        when(studentGiftService.processAsyncOrder(any(OrderCreateMessage.class))).thenReturn(false);
        when(studentGiftService.existsOrderByRequestId(eq(msg.studentId()), eq(msg.requestId())))
                .thenThrow(new RuntimeException("Database timeout during order verification"));

        consumer.handleOrderCreate(amqpMessage, channel, deliveryTag);

        // 状态无法断定，绝不盲目补偿库存
        verify(giftStockRedisService, never()).compensateStockOnce(anyLong(), anyInt(), anyString());
        verify(giftStockRedisService, never()).compensateStock(anyLong(), anyInt());
        // 不写消费日志与本地消息失败
        verify(mqConsumeLogMapper, never()).insertConsumeLog(anyString(), anyString(), anyString(), anyString(), anyInt());
        verify(localMessageService, never()).markFailed(anyString(), anyString());
        // 安全 nack 转死信
        verify(channel, times(1)).basicNack(eq(deliveryTag), eq(false), eq(false));
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("P1-5: 并发重复消费按成功收尾时清理失败标记抛异常 - 仍安全 ACK 正常收尾")
    void testHandleOrderCreate_ConcurrentDuplicate_ClearSeckillFailureThrows_StillAcks() throws IOException {
        OrderCreateMessage msg = sampleMsg();
        Message amqpMessage = createAmqpMessage(JSON.toJSONString(msg));

        when(mqConsumeLogMapper.existsConsumed(eq(msg.messageId()), anyString())).thenReturn(0);
        when(studentGiftService.processAsyncOrder(any(OrderCreateMessage.class))).thenReturn(false);
        when(studentGiftService.existsOrderByRequestId(eq(msg.studentId()), eq(msg.requestId()))).thenReturn(true);
        doThrow(new RuntimeException("Redis connection error"))
                .when(studentGiftService).clearSeckillFailure(eq(msg.studentId()), eq(msg.requestId()));

        consumer.handleOrderCreate(amqpMessage, channel, deliveryTag);

        verify(giftStockRedisService, never()).compensateStockOnce(anyLong(), anyInt(), anyString());
        verify(localMessageService, times(1)).markConsumed(eq(msg.messageId()));
        verify(channel, times(1)).basicAck(eq(deliveryTag), eq(false));
    }

    @Test
    @DisplayName("P1-5: 事务未提交异常补偿流程中记录消费日志或发件箱失败抛异常 - 仍安全 NACK 转死信")
    void testHandleOrderCreate_FatalException_MarkFailedThrows_StillNacks() throws IOException {
        OrderCreateMessage msg = sampleMsg();
        Message amqpMessage = createAmqpMessage(JSON.toJSONString(msg));

        when(mqConsumeLogMapper.existsConsumed(eq(msg.messageId()), anyString())).thenReturn(0);
        when(studentGiftService.processAsyncOrder(any(OrderCreateMessage.class)))
                .thenThrow(new RuntimeException("Database connection timeout"));
        when(studentGiftService.existsOrderByRequestId(eq(msg.studentId()), eq(msg.requestId()))).thenReturn(false);
        doThrow(new RuntimeException("local_message write fail"))
                .when(localMessageService).markFailed(eq(msg.messageId()), anyString());

        consumer.handleOrderCreate(amqpMessage, channel, deliveryTag);

        verify(giftStockRedisService, times(1)).compensateStockOnce(eq(msg.giftId()), eq(msg.quantity()), eq(msg.messageId()));
        verify(channel, times(1)).basicNack(eq(deliveryTag), eq(false), eq(false));
    }
}
