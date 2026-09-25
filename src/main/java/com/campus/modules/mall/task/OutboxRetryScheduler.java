package com.campus.modules.mall.task;

import com.campus.config.RabbitMqConfig;
import com.campus.modules.mall.entity.LocalMessage;
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

    private static final int MAX_RETRIES = 5;

    @Scheduled(fixedDelay = 5000)
    public void retryPendingMessages() {
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
                    localMessageService.markFailed(msg.getMessageId(), "超过最大重试次数: " + MAX_RETRIES);
                    continue;
                }

                // 尝试重新发送 MQ
                if ("GIFT_ORDER_CREATE".equals(msg.getBizType())) {
                    rabbitTemplate.convertAndSend(
                            RabbitMqConfig.SECKILL_ORDER_EXCHANGE,
                            RabbitMqConfig.SECKILL_ORDER_ROUTING_KEY,
                            msg.getPayload()
                    );
                }

                localMessageService.markPublished(msg.getMessageId());
                log.info("[Outbox补偿] 补偿投递成功: messageId={}, bizType={}", msg.getMessageId(), msg.getBizType());
            } catch (Exception e) {
                log.warn("[Outbox补偿] 补偿投递失败，累计重试次数: messageId={}, err={}",
                        msg.getMessageId(), e.getMessage());
                localMessageService.incrementRetry(msg.getMessageId(), msg.getRetryCount() + 1, e.getMessage());
            } finally {
                if (originalTraceId != null) {
                    MDC.put("traceId", originalTraceId);
                } else {
                    MDC.remove("traceId");
                }
            }
        }
    }
}
