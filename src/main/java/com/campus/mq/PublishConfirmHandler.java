package com.campus.mq;

import com.campus.common.metrics.CampusMetricsCollector;
import com.campus.modules.mall.service.LocalMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RabbitMQ 发布侧可靠性闭环：Publisher Confirm + Publisher Returns。
 *
 * <p><b>为什么需要它：</b>{@code convertAndSend} 只是把消息写进本地 Socket 缓冲区，
 * 既不保证 Broker 收到（连接断开、队列满、磁盘告警都会 NACK），也不保证消息能路由到队列
 * （交换机名/路由键写错时 Broker 会直接丢弃）。此前发件箱在 {@code convertAndSend} 返回后
 * 立刻把本地消息标记为「已投递」，于是「Broker 没收到」和「路由不到」这两类失败被静默吞掉：
 * 消息永不重投，秒杀订单永不落库，Redis 预扣库存被永久占用（少卖）。
 *
 * <p><b>闭环规则：</b>
 * <ul>
 *   <li>只有 Broker 返回 {@code basic.ack} 且消息未被 {@code basic.return}，才把本地消息置为「已投递」；</li>
 *   <li>{@code basic.nack} / 不可路由，一律把本地消息退回「待投递」并累加重试次数，
 *       交给 {@code OutboxRetryScheduler} 重投；重试耗尽后走原有的判死与库存回补逻辑；</li>
 *   <li>连接断开导致 confirm 始终不到达时，消息停留在「待投递」，下一轮 Outbox 扫描会重投。</li>
 * </ul>
 *
 * <p>由于 AMQP 协议保证 {@code basic.return} 先于 {@code basic.ack} 到达，这里用一张短 TTL
 * 的内存表记住「刚被退回的 messageId」，让紧随其后的 ack 不再把它误标为已投递。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PublishConfirmHandler implements RabbitTemplate.ConfirmCallback, RabbitTemplate.ReturnsCallback {

    /** 消息 ID 的 AMQP header 名，用于把 basic.return 与发件箱记录关联起来。 */
    public static final String HEADER_MESSAGE_ID = "x-message-id";

    /** 退回标记的保留时长：只需覆盖「return → ack」这段毫秒级窗口。 */
    private static final long RETURNED_MARK_TTL_MS = 60_000L;

    /** 标记表容量上限，超过后先清理过期项，避免长期运行内存无界增长。 */
    private static final int RETURNED_MARK_CLEAN_THRESHOLD = 1024;

    private final LocalMessageService localMessageService;
    private final CampusMetricsCollector metricsCollector;

    /** messageId -> 最近一次 basic.return 的时间戳。 */
    private final Map<String, Long> returnedMessages = new ConcurrentHashMap<>();

    @Override
    public void confirm(org.springframework.amqp.rabbit.connection.CorrelationData correlation, boolean ack, String cause) {
        String messageId = correlation != null ? correlation.getId() : null;
        if (!StringUtils.hasText(messageId)) {
            // 非发件箱消息（例如运营手工投递）没有关联 ID，无法回写状态，仅记录结果
            if (!ack) {
                log.error("[MQ发布确认] Broker 拒绝了未携带关联ID的消息，无法自动重投，请人工核查: cause={}", cause);
            }
            return;
        }

        if (!ack) {
            log.error("[MQ发布确认] Broker 返回 NACK，本地消息退回待投递并等待重投: messageId={}, cause={}", messageId, cause);
            resetToPending(messageId, "Broker NACK: " + cause);
            return;
        }

        if (isRecentlyReturned(messageId)) {
            // 交换机收到但路由不到任何队列：消息已被 Broker 丢弃，绝不能标记为已投递
            log.error("[MQ发布确认] 消息虽被确认但此前已被 basic.return（无匹配队列），保持待投递以触发重投: messageId={}",
                    messageId);
            resetToPending(messageId, "消息不可路由(basic.return)，无匹配队列");
            return;
        }

        try {
            localMessageService.markPublished(messageId);
            log.debug("[MQ发布确认] Broker 已确认接收，本地消息标记为已投递: messageId={}", messageId);
        } catch (Exception e) {
            // 状态回写失败不影响消息已送达的事实；Outbox 若重投，消费端幂等可兜底
            log.warn("[MQ发布确认] 标记已投递失败，等待下一轮对账修复: messageId={}, err={}", messageId, e.getMessage());
        }
    }

    @Override
    public void returnedMessage(ReturnedMessage returned) {
        if (returned == null || returned.getMessage() == null) {
            return;
        }
        Object headerValue = returned.getMessage().getMessageProperties().getHeaders().get(HEADER_MESSAGE_ID);
        String messageId = headerValue != null ? headerValue.toString() : null;
        log.error("[MQ消息退回] 消息无法路由到任何队列，已被 Broker 退回: messageId={}, exchange={}, routingKey={}, replyCode={}, replyText={}",
                messageId, returned.getExchange(), returned.getRoutingKey(), returned.getReplyCode(), returned.getReplyText());

        if (!StringUtils.hasText(messageId)) {
            return;
        }
        cleanupExpiredMarks();
        returnedMessages.put(messageId, System.currentTimeMillis());
        resetToPending(messageId, String.format("消息不可路由: exchange=%s, routingKey=%s, reply=%s",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
    }

    /** 是否在 TTL 窗口内被 basic.return 过。 */
    private boolean isRecentlyReturned(String messageId) {
        Long returnedAt = returnedMessages.get(messageId);
        if (returnedAt == null) {
            return false;
        }
        return System.currentTimeMillis() - returnedAt <= RETURNED_MARK_TTL_MS;
    }

    /** 清理过期退回标记，避免内存无界增长。 */
    private void cleanupExpiredMarks() {
        if (returnedMessages.size() < RETURNED_MARK_CLEAN_THRESHOLD) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> iterator = returnedMessages.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (now - entry.getValue() > RETURNED_MARK_TTL_MS) {
                iterator.remove();
            }
        }
    }

    /**
     * 把本地消息退回「待投递」并累计重试次数。
     *
     * <p>重试次数必须累加：否则一条永远无法路由的消息会被 Outbox 无限重投，
     * 既不判死也不告警，形成隐性消息风暴。
     */
    private void resetToPending(String messageId, String reason) {
        try {
            int changed = localMessageService.resetToPending(messageId, reason, LocalDateTime.now().plusSeconds(5));
            if (changed > 0) {
                metricsCollector.recordOutboxRetry();
                log.warn("[MQ发布确认] 已把本地消息退回待投递，等待发件箱重投: messageId={}, reason={}", messageId, reason);
            }
        } catch (Exception e) {
            log.error("[MQ发布确认] 退回待投递失败，消息可能停留在已投递状态: messageId={}, err={}", messageId, e.getMessage());
        }
    }
}
