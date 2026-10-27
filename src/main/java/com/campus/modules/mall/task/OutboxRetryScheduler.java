package com.campus.modules.mall.task;

import com.alibaba.fastjson2.JSON;
import com.campus.common.metrics.CampusMetricsCollector;
import com.campus.config.RabbitMqConfig;
import com.campus.modules.mall.dto.OrderCreateMessage;
import com.campus.modules.mall.entity.LocalMessage;
import com.campus.modules.mall.service.GiftStockRedisService;
import com.campus.modules.mall.service.LocalMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 事务性发件箱 (Transactional Outbox) 超时重投补偿定时任务 (P1-3)
 * 定时扫描状态为 0 (待投递) 的本地消息，执行补偿重发，彻底杜绝消息丢失少卖死穴
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "campus.schedule.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRetryScheduler {

    private final LocalMessageService localMessageService;
    private final RabbitTemplate rabbitTemplate;
    private final GiftStockRedisService giftStockRedisService;
    private final CampusMetricsCollector metricsCollector;

    private static final int MAX_RETRIES = 5;

    @Scheduled(fixedDelay = 5000)
    public void retryPendingMessages() {
        // 0. 先用消费终态日志修复「订单已成功/已拒绝，但本地消息状态丢失」的窗口
        try {
            int repaired = localMessageService.repairTerminalStatusFromConsumeLog();
            if (repaired > 0) {
                log.warn("[Outbox状态修复] 已用消费日志补齐 {} 条本地消息终态，避免在途预扣虚高", repaired);
            }
        } catch (Exception e) {
            log.warn("[Outbox状态修复] 本轮状态修复失败，等待下一轮: {}", e.getMessage());
        }

        // 1. 实时采集并上报发件箱待投递积压 Gauge (R4)
        long backlog = localMessageService.countPendingBacklog();
        metricsCollector.updateOutboxBacklog(backlog);

        List<LocalMessage> pendingMessages = localMessageService.getPendingMessages(MAX_RETRIES, 50);
        if (pendingMessages.isEmpty()) {
            return;
        }

        log.info("[Outbox补偿] 扫描到 {} 条待投递/待补偿重发消息，开始执行重投...", pendingMessages.size());

        for (LocalMessage msg : pendingMessages) {
            String originalTraceId = MDC.get("traceId");
            try {
                if (msg.getTraceId() != null) {
                    MDC.put("traceId", msg.getTraceId());
                }

                if (msg.getRetryCount() >= MAX_RETRIES) {
                    log.error("[Outbox报警] 消息投递达到最大重试次数({})，标记为死亡状态需人工介入: messageId={}, bizId={}",
                            MAX_RETRIES, msg.getMessageId(), msg.getBizId());
                    // 判死意味着这笔订单永不落库，秒杀入口预扣的 Redis 库存必须回补，
                    // 否则该库存被永久占用（表现为「显示已抢光却无人下单」的少卖）。
                    compensatePreDeductedStock(msg, "投递重试超限");
                    localMessageService.markFailed(msg.getMessageId(), "超过最大重试次数: " + MAX_RETRIES);
                    metricsCollector.recordOutboxDead();
                    sendAlertNotification("OUTBOX_DEAD_LETTER", msg.getMessageId(), "投递重试超限被判死");
                    continue;
                }

                // 尝试重新发送 MQ
                if (!"GIFT_ORDER_CREATE".equals(msg.getBizType())) {
                    log.error("[Outbox告警] 未知业务类型，无法决定投递目标，标记失败待人工介入: messageId={}, bizType={}",
                            msg.getMessageId(), msg.getBizType());
                    compensatePreDeductedStock(msg, "未知业务类型");
                    localMessageService.markFailed(msg.getMessageId(), "未支持的 bizType: " + msg.getBizType());
                    metricsCollector.recordOutboxDead();
                    sendAlertNotification("OUTBOX_UNKNOWN_TYPE", msg.getMessageId(), "未知业务类型: " + msg.getBizType());
                    continue;
                }

                OrderCreateMessage payload = JSON.parseObject(msg.getPayload(), OrderCreateMessage.class);
                rabbitTemplate.convertAndSend(
                        RabbitMqConfig.SECKILL_ORDER_EXCHANGE,
                        RabbitMqConfig.SECKILL_ORDER_ROUTING_KEY,
                        payload
                );

                localMessageService.markPublished(msg.getMessageId());
                log.info("[Outbox补偿] 补偿投递成功: messageId={}, bizType={}", msg.getMessageId(), msg.getBizType());
            } catch (Exception e) {
                log.warn("[Outbox补偿] 补偿投递失败，累计重试次数: messageId={}, err={}",
                        msg.getMessageId(), e.getMessage());
                localMessageService.incrementRetry(msg.getMessageId(), msg.getRetryCount() + 1, e.getMessage());
                metricsCollector.recordOutboxRetry();
            } finally {
                if (originalTraceId != null) {
                    MDC.put("traceId", originalTraceId);
                } else {
                    MDC.remove("traceId");
                }
            }
        }
    }

    /**
     * 消息被判为死亡（重试超限 / 未知业务类型）时，回补它在秒杀入口预扣的 Redis 库存。
     *
     * <p><b>为什么必须回补：</b>消息判死意味着这笔订单永远不会落库，那笔 Redis 预扣不再有
     * 任何对应的订单来消化，若不回补就被永久占用 —— 即「用户看到抢光却无人下单」的少卖。
     * 前序补偿链路（{@code OrderCreateConsumer} 的消费异常/业务拒绝补偿、秒杀入口的
     * 事务回滚补偿）都覆盖不到这条路径：消息从未被成功投递，消费端自然不会补偿。
     *
     * <p>回补走按 messageId 去重的一次性 Lua 脚本：同一判死消息即使被重复扫描，
     * Redis 库存也最多加回一次；库存 key 不存在时不会凭空 SET 库存。
     *
     * @param message 被判死的发件箱消息
     * @param reason  判死原因，仅用于日志归因
     */
    private void compensatePreDeductedStock(LocalMessage message, String reason) {
        try {
            OrderCreateMessage payload = JSON.parseObject(message.getPayload(), OrderCreateMessage.class);
            if (payload == null || payload.giftId() == null) {
                log.warn("[Outbox补偿] 判死消息缺少 giftId，库存回补跳过: messageId={}, reason={}",
                        message.getMessageId(), reason);
                return;
            }
            int quantity = payload.quantity() != null && payload.quantity() > 0 ? payload.quantity() : 1;
            int result = giftStockRedisService.compensateStockOnce(payload.giftId(), quantity, message.getMessageId());
            if (result == 1) {
                log.warn("[Outbox补偿] 消息判死（{}），已一次性回补 Redis 预扣库存: messageId={}, giftId={}, quantity={}",
                        reason, message.getMessageId(), payload.giftId(), quantity);
            } else {
                log.warn("[Outbox补偿] 消息判死（{}），库存未由本路径回补: messageId={}, result={}",
                        reason, message.getMessageId(), result);
            }
        } catch (Exception e) {
            log.warn("[Outbox补偿] 判死消息库存回补失败，交由定时对账兜底: messageId={}, err={}",
                    message.getMessageId(), e.getMessage());
        }
    }

    /**
     * 生产级告警通知通道：死信累积时触发结构化告警事件 (R4 告警闭环)
     */
    private void sendAlertNotification(String alertEvent, String messageId, String reason) {
        log.error("[ALERT-CHANNEL] 🚨 触发死信报警通知通道: event={}, messageId={}, reason={}, timestamp={}",
                alertEvent, messageId, reason, System.currentTimeMillis());
    }
}
