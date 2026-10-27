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
            // Jackson2JsonMessageConverter 在发送 String 时会再包一层 JSON 字符串；
            // 历史在途消息和旧重投消息可能仍是双层编码，这里兼容单层对象与双层字符串。
            Object parsedPayload = JSON.parse(rawPayload);
            String normalizedPayload = parsedPayload instanceof String jsonString ? jsonString : rawPayload;
            msg = JSON.parseObject(normalizedPayload, OrderCreateMessage.class);
        } catch (Exception e) {
            log.error("[MQ消费异常] 消息格式损坏，判定为无法解析的毒消息，路由至死信队列: payload={}", rawPayload, e);
            // 毒消息同样已预扣过 Redis 库存，必须尽力回补，否则这笔库存被永久占用（少卖）。
            // 反序列化失败拿不到强类型消息，退化为正则提取 giftId/quantity 尝试回补；
            // 提取失败则只能由 StockReconciliationScheduler 的 5 分钟对账兜底。
            compensateIfParsable(rawPayload);
            // requeue = false -> 自动转入死信队列
            nackSafely(channel, deliveryTag, "unparseable");
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
                // 补偿上一轮可能发生的「订单已提交，但本地消息未标记完成」状态
                markConsumedSafely(msg.messageId());
                ackSafely(channel, deliveryTag, msg.messageId());
                return;
            }

            // 2. 事务落库处理。成功提交后，后续标记/ACK 失败绝不能反向回补库存。
            boolean success;
            try {
                success = studentGiftService.processAsyncOrder(msg);
            } catch (Exception businessException) {
                boolean orderPersisted;
                try {
                    orderPersisted = studentGiftService.existsOrderByRequestId(msg.studentId(), msg.requestId());
                } catch (Exception verifyException) {
                    log.error("[MQ消费异常] 落库抛异常且无法确认订单是否已提交，转死信等待人工核查，不盲目补偿库存: "
                                    + "messageId={}, requestId={}, cause={}, verifyError={}",
                            msg.messageId(), msg.requestId(), businessException.getMessage(), verifyException.getMessage());
                    nackSafely(channel, deliveryTag, msg.messageId());
                    return;
                }

                if (!orderPersisted) {
                    log.error("[MQ消费异常] 订单事务未提交，执行一次性库存补偿后转死信: messageId={}, requestId={}",
                            msg.messageId(), msg.requestId(), businessException);
                    compensateOnce(msg, "订单事务未提交");
                    nackSafely(channel, deliveryTag, msg.messageId());
                    return;
                }

                // 极端边界：事务实际已提交，但代理仍抛异常。此时订单已落库，禁止回补库存。
                log.warn("[MQ消费异常] 检测到订单已落库，按成功路径收尾并禁止库存补偿: messageId={}, requestId={}",
                        msg.messageId(), msg.requestId());
                success = true;
            }

            if (success) {
                recordConsumeLogSafely(msg, 1);
                markConsumedSafely(msg.messageId());
                ackSafely(channel, deliveryTag, msg.messageId());
                log.info("[MQ异步落库] 订单事务处理完毕并确认 ACK: messageId={}, requestId={}",
                        msg.messageId(), msg.requestId());
            } else {
                // 业务拒绝 (如余额不足或账户冻结)：库存只回补一次，后续任何标记/ACK 异常都不得再次回补。
                log.warn("[MQ业务拒绝] 异步落库因业务校验未通过，触发一次性库存回补: messageId={}",
                        msg.messageId());
                compensateOnce(msg, "业务校验未通过");
                recordConsumeLogSafely(msg, 2);
                markFailedSafely(msg.messageId(), "业务校验未通过");
                ackSafely(channel, deliveryTag, msg.messageId());
            }
        } catch (Exception e) {
            // 此处只处理无法判断业务是否落库的协调异常：不回补库存，转死信人工核查，
            // 避免把已成交订单的预扣再次加回 Redis。
            log.error("[MQ消费异常] 消费协调阶段发生异常，转死信且不盲目补偿库存: messageId={}, err={}",
                    msg != null ? msg.messageId() : "null", e.getMessage(), e);
            nackSafely(channel, deliveryTag, msg != null ? msg.messageId() : "null");
        } finally {
            MDC.remove("traceId");
        }
    }

    private void recordConsumeLogSafely(OrderCreateMessage msg, int status) {
        try {
            mqConsumeLogMapper.insertConsumeLog(
                    msg.messageId(), CONSUMER_NAME, "GIFT_ORDER_CREATE", msg.requestId(), status);
        } catch (Exception ex) {
            log.warn("[MQ幂等记录] 写入消费记录失败(不改变已提交的订单事实): messageId={}, status={}, err={}",
                    msg.messageId(), status, ex.getMessage());
        }
    }

    private void markConsumedSafely(String messageId) {
        try {
            localMessageService.markConsumed(messageId);
        } catch (Exception e) {
            log.warn("[MQ本地消息] 标记消费完成失败，等待重投时补记: messageId={}, err={}",
                    messageId, e.getMessage());
        }
    }

    private void markFailedSafely(String messageId, String reason) {
        try {
            localMessageService.markFailed(messageId, reason);
        } catch (Exception e) {
            log.warn("[MQ本地消息] 标记业务失败失败: messageId={}, err={}", messageId, e.getMessage());
        }
    }

    private void ackSafely(Channel channel, long deliveryTag, String messageId) {
        try {
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            // 不能因为 ACK 失败而回补库存；消息重投后会命中消费幂等表。
            log.warn("[MQ ACK] 手动确认失败，消息可能重投但订单不会重复落库: messageId={}, err={}",
                    messageId, e.getMessage());
        }
    }

    private void nackSafely(Channel channel, long deliveryTag, String messageId) {
        try {
            channel.basicNack(deliveryTag, false, false);
        } catch (Exception e) {
            log.warn("[MQ NACK] 拒绝消息失败: messageId={}, err={}", messageId, e.getMessage());
        }
    }

    private void compensateOnce(OrderCreateMessage msg, String reason) {
        int result = giftStockRedisService.compensateStockOnce(msg.giftId(), msg.quantity(), msg.messageId());
        if (result != 1) {
            log.warn("[MQ库存补偿] 未完成本次回补，result={}, reason={}, messageId={}",
                    result, reason, msg.messageId());
        }
    }

    /**
     * 毒消息场景下的尽力库存回补：反序列化失败时 {@code msg} 为 null，无法走强类型分支。
     * <p>用正则从原始 JSON 载荷中提取 {@code messageId/giftId/quantity} 后按 messageId 幂等回补，
     * 避免「消息损坏进死信 + Redis 库存被永久占用」的少卖，也避免同一毒消息重复投递时重复加库存。
     * <p>提取失败（如载荷被截断）时仅告警，兜底交由 {@code StockReconciliationScheduler}
     * 以 MySQL 为准的定时对账修复。
     *
     * @param rawPayload 原始消息载荷
     */
    private void compensateIfParsable(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            return;
        }
        try {
            String messageIdRaw = extractJsonField(rawPayload, "messageId");
            String giftIdRaw = extractJsonField(rawPayload, "giftId");
            String quantityRaw = extractJsonField(rawPayload, "quantity");
            if (messageIdRaw == null || giftIdRaw == null || quantityRaw == null) {
                log.warn("[MQ毒消息] 未能从损坏载荷中提取 messageId/giftId/quantity，库存回补跳过，交由定时对账兜底: payload={}",
                        rawPayload);
                return;
            }
            long giftId = Long.parseLong(giftIdRaw.trim());
            int quantity = Integer.parseInt(quantityRaw.trim());
            if (quantity <= 0) {
                log.warn("[MQ毒消息] 损坏载荷中 quantity 非法，库存回补跳过: giftId={}, quantity={}", giftId, quantity);
                return;
            }
            int result = giftStockRedisService.compensateStockOnce(giftId, quantity, messageIdRaw.trim());
            log.info("[MQ毒消息] 已按 messageId 幂等回补库存: giftId={}, quantity={}, messageId={}, result={}",
                    giftId, quantity, messageIdRaw, result);
        } catch (Exception ex) {
            log.warn("[MQ毒消息] 损坏载荷库存回补失败，交由定时对账兜底: payload={}, err={}", rawPayload, ex.getMessage());
        }
    }

    /**
     * 从扁平 JSON 文本中提取指定字段的原始值（支持字符串/数字/布尔三种形态，不处理嵌套对象与转义）。
     *
     * @param json JSON 文本
     * @param field 字段名
     * @return 字段值的原始文本；不存在或格式不匹配时返回 null
     */
    private String extractJsonField(String json, String field) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"" + java.util.regex.Pattern.quote(field) + "\"\\s*:\\s*(\"([^\"]*)\"|[^,}\\s]+)")
                .matcher(json);
        if (!m.find()) {
            return null;
        }
        // 分组 2 为带引号的字符串值（可能为空串），分组 1 覆盖两种形态
        return m.group(2) != null ? m.group(2) : m.group(1);
    }
}
