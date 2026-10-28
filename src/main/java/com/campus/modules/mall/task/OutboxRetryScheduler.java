package com.campus.modules.mall.task;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.campus.common.metrics.CampusMetricsCollector;
import com.campus.config.RabbitMqConfig;
import com.campus.modules.mall.dto.OrderCreateMessage;
import com.campus.modules.mall.entity.LocalMessage;
import com.campus.modules.mall.service.GiftStockRedisService;
import com.campus.modules.mall.service.LocalMessageService;
import com.campus.modules.mall.service.StudentGiftService;
import com.campus.mq.MqPublishSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 事务性发件箱 (Transactional Outbox) 超时重投补偿定时任务 (P1-3)
 *
 * <p>定时扫描状态为 0 (待投递) 的本地消息执行补偿重发，彻底杜绝消息丢失少卖死穴。
 *
 * <p><b>「重投」与「送达」是两件事：</b>本任务只负责把消息重新交给 Broker，
 * 不再在发送调用返回后把本地消息标成已投递 —— 那个判断由 {@code PublishConfirmHandler}
 * 依据 Broker 的 basic.ack 完成。此前在发送后立即 markPublished，导致 NACK 与不可路由
 * 都被当成成功，消息永不重投。
 *
 * <p><b>覆盖两类消息：</b>
 * <ul>
 *   <li>{@code GIFT_ORDER_CREATE}：秒杀下单。判死时必须回补 Redis 预扣库存并写失败标记。</li>
 *   <li>{@code GIFT_ORDER_DELAY}：24 小时延时关单。判死只告警，订单由
 *       {@code GiftOrderTimeoutScheduler} 扫库兜底关单，不会因为一条消息丢失而永不关单。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "campus.schedule.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRetryScheduler {

    /** 业务类型：秒杀下单（Redis 预扣 → MQ → 异步落库）。 */
    public static final String BIZ_ORDER_CREATE = "GIFT_ORDER_CREATE";
    /** 业务类型：24 小时延时关单。 */
    public static final String BIZ_ORDER_DELAY = "GIFT_ORDER_DELAY";

    /** 最大重试次数与查询上界共用同一来源，避免两处写死不同数字导致永久卡死。 */
    private static final int MAX_RETRIES = LocalMessageService.MAX_PUBLISH_RETRIES;

    /**
     * 「已投递却长期无消费记录」的判定阈值。
     * 正常消费在秒级完成；超过 10 分钟仍无消费日志，基本可判定消息在 Broker 侧丢了或被长期搁置。
     */
    private static final Duration STALE_PUBLISHED_THRESHOLD = Duration.ofMinutes(10);

    private final LocalMessageService localMessageService;
    private final RabbitTemplate rabbitTemplate;
    private final GiftStockRedisService giftStockRedisService;
    private final CampusMetricsCollector metricsCollector;
    private final StudentGiftService studentGiftService;

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

        // 0.1 兜底重投「已投递却长期无消费记录」的秒杀下单消息。
        //     发布确认只证明 Broker 收到，不证明消息最终被消费：队列落盘前宕机、镜像切换丢消息、
        //     消费端长期停机，都会让消息停在 status=1 永不重投。这里按「超时 + 无消费日志」捞回来。
        try {
            int requeued = localMessageService.requeueStalePublishedMessages(STALE_PUBLISHED_THRESHOLD, 20);
            if (requeued > 0) {
                log.warn("[Outbox兜底] 检测到 {} 条已投递却长期无消费记录的秒杀消息，已退回待重投", requeued);
                metricsCollector.recordOutboxRetry();
            }
        } catch (Exception e) {
            log.warn("[Outbox兜底] 本轮在途消息对账失败，等待下一轮: {}", e.getMessage());
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

                if (msg.getRetryCount() != null && msg.getRetryCount() >= MAX_RETRIES) {
                    handleDeadMessage(msg);
                    continue;
                }

                dispatch(msg);
                // 注意：这里不 markPublished。消息是否真的被 Broker 接收，由 confirm 回调决定；
                // 提前标记会把「发送调用没抛异常」误当成「Broker 已收到」，一旦 Broker 拒收消息就永久丢失。
                log.info("[Outbox补偿] 已重新提交至 Broker，等待发布确认: messageId={}, bizType={}",
                        msg.getMessageId(), msg.getBizType());
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
     * 按业务类型把消息重新投递到对应的交换机与队列。
     *
     * <p>未知类型直接抛异常进入重试计数，最终判死并告警：静默丢弃会让一条无法归类的消息
     * 永远占用发件箱容量，且无人知晓它本该做什么。
     */
    private void dispatch(LocalMessage message) {
        String bizType = message.getBizType();

        if (BIZ_ORDER_CREATE.equals(bizType)) {
            OrderCreateMessage payload = JSON.parseObject(message.getPayload(), OrderCreateMessage.class);
            if (payload == null) {
                throw new IllegalStateException("秒杀下单消息体解析为空: " + message.getMessageId());
            }
            MqPublishSupport.publish(rabbitTemplate,
                    RabbitMqConfig.SECKILL_ORDER_EXCHANGE,
                    RabbitMqConfig.SECKILL_ORDER_ROUTING_KEY,
                    payload,
                    message.getMessageId());
            return;
        }

        if (BIZ_ORDER_DELAY.equals(bizType)) {
            Map<String, Object> payload = JSON.parseObject(
                    message.getPayload(), new TypeReference<Map<String, Object>>() {});
            if (payload == null || payload.isEmpty()) {
                throw new IllegalStateException("延时关单消息体解析为空: " + message.getMessageId());
            }
            MqPublishSupport.publish(rabbitTemplate,
                    RabbitMqConfig.GIFT_ORDER_EXCHANGE,
                    RabbitMqConfig.GIFT_ORDER_DELAY_ROUTING_KEY,
                    payload,
                    message.getMessageId());
            return;
        }

        throw new IllegalArgumentException("未支持的 bizType: " + bizType);
    }

    /**
     * 消息被判死（重试超限 / 未知业务类型）时的收尾。
     *
     * <p>秒杀下单消息判死意味着订单永远不会落库：必须回补它在入口预扣的 Redis 库存，
     * 并写失败标记让轮询接口返回终局，否则用户会永久停在「排队中」且库存被永久占用。
     *
     * <p>延时关单消息判死不影响资金正确性：订单仍有 {@code GiftOrderTimeoutScheduler}
     * 以 MySQL 为准兜底关单，这里只做告警，不回补任何库存（关单消息从不预扣库存）。
     */
    private void handleDeadMessage(LocalMessage message) {
        String bizType = message.getBizType();
        log.error("[Outbox报警] 消息投递达到最大重试次数({})，标记为死亡状态需人工介入: messageId={}, bizType={}, bizId={}",
                MAX_RETRIES, message.getMessageId(), bizType, message.getBizId());

        if (BIZ_ORDER_CREATE.equals(bizType)) {
            compensatePreDeductedStock(message, "投递重试超限");
            markOrderCreateFailed(message, "系统繁忙，本次兑换未完成，请稍后重试");
        } else if (BIZ_ORDER_DELAY.equals(bizType)) {
            log.error("[Outbox报警] 延时关单消息投递重试超限，该订单将由超时兜底扫描任务关单: bizId={}, messageId={}",
                    message.getBizId(), message.getMessageId());
        } else {
            compensatePreDeductedStock(message, "未知业务类型");
        }

        localMessageService.markFailed(message.getMessageId(), "超过最大重试次数: " + MAX_RETRIES);
        metricsCollector.recordOutboxDead();
        sendAlertNotification("OUTBOX_DEAD_LETTER", message.getMessageId(), "投递重试超限: " + bizType);
    }

    /**
     * 秒杀下单消息判死时写入失败标记，让轮询接口从「处理中」变成明确的失败终局。
     *
     * <p>取不到 studentId / requestId 时（历史消息格式）只告警：宁可让客户端超时，
     * 也不能给错误的 requestId 写标记，否则会误伤同学生的另一次兑换。
     */
    private void markOrderCreateFailed(LocalMessage message, String reason) {
        try {
            OrderCreateMessage payload = JSON.parseObject(message.getPayload(), OrderCreateMessage.class);
            if (payload == null || payload.studentId() == null || payload.requestId() == null) {
                log.warn("[Outbox补偿] 判死消息缺少 studentId/requestId，无法写失败标记: messageId={}",
                        message.getMessageId());
                return;
            }
            studentGiftService.markSeckillFailed(payload.studentId(), payload.requestId(), reason);
        } catch (Exception e) {
            log.warn("[Outbox补偿] 写入秒杀失败标记异常: messageId={}, err={}", message.getMessageId(), e.getMessage());
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
