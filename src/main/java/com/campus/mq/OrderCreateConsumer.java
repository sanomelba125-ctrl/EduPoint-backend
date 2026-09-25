package com.campus.mq;

import com.alibaba.fastjson2.JSON;
import com.campus.config.RabbitMqConfig;
import com.campus.modules.mall.dto.OrderCreateMessage;
import com.campus.modules.mall.mapper.MqConsumeLogMapper;
import com.campus.modules.mall.service.GiftStockRedisService;
import com.campus.modules.mall.service.LocalMessageService;
import com.campus.modules.mall.service.StudentGiftService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 秒杀订单异步削峰消费者 (P1-2 & P1-4)
 * 具备: 手动 ACK + 业务强幂等表 + 毒消息死信路由 (DLX) + 失败反向库存补偿
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreateConsumer {

    private static final String CONSUMER_NAME = "OrderCreateConsumer";

    private final StudentGiftService studentGiftService;
    private final LocalMessageService localMessageService;
    private final GiftStockRedisService giftStockRedisService;
    private final MqConsumeLogMapper mqConsumeLogMapper;

    @RabbitListener(queues = RabbitMqConfig.SECKILL_ORDER_QUEUE)
    public void handleOrderCreate(Message amqpMessage,
                                  Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        String rawPayload = new String(amqpMessage.getBody(), StandardCharsets.UTF_8);
        String headerTraceId = (String) amqpMessage.getMessageProperties().getHeaders().get("x-trace-id");

        OrderCreateMessage msg = null;
        try {
            msg = JSON.parseObject(rawPayload, OrderCreateMessage.class);
        } catch (Exception e) {
            log.error("[MQ消费异常] 消息格式损坏，判定为无法解析的毒消息，路由至死信队列: payload={}", rawPayload, e);
            // requeue = false -> 自动转入死信队列
            channel.basicNack(deliveryTag, false, false);
            return;
        }

        String traceId = StringUtils.hasText(headerTraceId) ? headerTraceId : (msg != null ? msg.traceId() : null);
        if (StringUtils.hasText(traceId)) {
            MDC.put("traceId", traceId);
        }

        try {
            log.info("[MQ异步落库] 接收到秒杀下单消息: messageId={}, studentId={}, giftId={}, qty={}",
                    msg.messageId(), msg.studentId(), msg.giftId(), msg.quantity());

            // 1. 消费端强幂等前置拦截 (P1-4)
            int consumed = mqConsumeLogMapper.existsConsumed(msg.messageId(), CONSUMER_NAME);
            if (consumed > 0) {
                log.warn("[MQ幂等防重] 消息已被消费过，直接 ACK 忽略重复消费: messageId={}", msg.messageId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 2. 事务落库处理
            boolean success = studentGiftService.processAsyncOrder(msg);

            if (success) {
                // 记录消费幂等表
                try {
                    mqConsumeLogMapper.insertConsumeLog(msg.messageId(), CONSUMER_NAME, "GIFT_ORDER_CREATE", msg.requestId(), 1);
                } catch (Exception ex) {
                    log.warn("[MQ幂等记录] 插入消费记录撞唯一约束(并发幂等已保障): messageId={}", msg.messageId());
                }

                // 标记本地消息表为完成
                localMessageService.markConsumed(msg.messageId());

                // 手动确认消息
                channel.basicAck(deliveryTag, false);
                log.info("[MQ异步落库] 订单事务处理完毕并确认 ACK: messageId={}, requestId={}", msg.messageId(), msg.requestId());
            } else {
                // 业务拒绝 (如余额不足或账户冻结): 触发反向回补库存并拒绝重新入队
                log.warn("[MQ业务拒绝] 异步落库因业务校验未通过，触发 Redis 库存反向回补: messageId={}", msg.messageId());
                giftStockRedisService.compensateStock(msg.giftId(), msg.quantity());
                try {
                    mqConsumeLogMapper.insertConsumeLog(msg.messageId(), CONSUMER_NAME, "GIFT_ORDER_CREATE", msg.requestId(), 2);
                } catch (Exception ex) {
                    log.debug("[MQ业务拒绝] 记录失败流水异常(可能已存在幂等记录): messageId={}, error={}", msg.messageId(), ex.getMessage());
                }
                localMessageService.markFailed(msg.messageId(), "业务校验未通过");
                channel.basicAck(deliveryTag, false);
            }
        } catch (Exception e) {
            log.error("[MQ消费异常] 消费执行发生未捕获异常: messageId={}, err={}", msg != null ? msg.messageId() : "null", e.getMessage(), e);
            // 反向回补预扣的库存，避免少卖
            if (msg != null) {
                giftStockRedisService.compensateStock(msg.giftId(), msg.quantity());
            }
            // 路由至死信队列，绝不无限次重投阻塞消费者
            channel.basicNack(deliveryTag, false, false);
        } finally {
            MDC.remove("traceId");
        }
    }
}
