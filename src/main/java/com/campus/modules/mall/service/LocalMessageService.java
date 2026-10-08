package com.campus.modules.mall.service;

import com.alibaba.fastjson2.JSON;
import com.campus.modules.mall.dto.OrderCreateMessage;
import com.campus.modules.mall.entity.LocalMessage;
import com.campus.modules.mall.mapper.LocalMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 事务性发件箱 (Transactional Outbox) 核心服务 (P1-3)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalMessageService {

    private final LocalMessageMapper localMessageMapper;

    /**
     * 在业务本地事务中同步落库待投递消息 (保证业务操作与消息投递原子性)
     */
    @Transactional(rollbackFor = Exception.class)
    public void savePendingMessage(String messageId, String bizType, String bizId, Object payload, String traceId) {
        LocalMessage msg = new LocalMessage();
        msg.setMessageId(messageId);
        msg.setBizType(bizType);
        msg.setBizId(bizId);
        msg.setPayload(JSON.toJSONString(payload));
        msg.setStatus(0); // 0-待投递
        msg.setRetryCount(0);
        msg.setNextRetryTime(LocalDateTime.now().plusSeconds(5)); // 5秒后首次补偿重试
        msg.setTraceId(traceId);
        msg.setCreateTime(LocalDateTime.now());
        msg.setUpdateTime(LocalDateTime.now());

        localMessageMapper.insert(msg);
        log.info("[Outbox本地消息] 成功插入待发送消息: messageId={}, bizType={}, bizId={}", messageId, bizType, bizId);
    }

    /**
     * 标记消息已发送至 Broker
     */
    public void markPublished(String messageId) {
        localMessageMapper.markAsPublished(messageId);
    }

    /**
     * 标记消息已被消费端成功处理
     */
    public void markConsumed(String messageId) {
        localMessageMapper.markAsConsumed(messageId);
    }

    /**
     * 标记消息达到重试上限失败
     */
    public void markFailed(String messageId, String reason) {
        localMessageMapper.markAsFailed(messageId, 3, reason);
    }

    /**
     * 递增重试次数并设置下一次重试时间
     */
    public void incrementRetry(String messageId, int retryCount, String reason) {
        // 指数退避: 5s, 10s, 20s, 40s...
        long backoffSeconds = (long) Math.pow(2, retryCount) * 5L;
        LocalDateTime nextTime = LocalDateTime.now().plusSeconds(backoffSeconds);
        localMessageMapper.incrementRetry(messageId, nextTime, reason);
    }

    /**
     * 查询超时未投递的消息
     */
    public List<LocalMessage> getPendingMessages(int maxRetries, int limit) {
        return localMessageMapper.selectPendingMessages(LocalDateTime.now(), maxRetries, limit);
    }

    /**
     * 统计「在途预扣」件数并按礼品 ID 聚合，供库存对账使用。
     *
     * <p><b>为什么对账需要它：</b>秒杀链路先在 Redis 原子预扣、再由 MQ 异步落库扣减 MySQL。
     * 在消息被消费完成之前，Redis 可用库存<b>本来就应当</b>低于 MySQL 实存库存，
     * 差额正是在途预扣。对账若直接拿 MySQL 库存覆盖 Redis，就会抹掉这批在途预扣，
     * 让 Redis 凭空多出库存被重复预扣 —— 形成超卖窗口。
     *
     * <p>消息体由同一 {@link OrderCreateMessage} 契约序列化而来，反序列化失败或缺少 giftId 时
     * 跳过该条并告警：宁可少扣在途量（对账偏保守）也不能算错。
     *
     * @return 礼品 ID → 在途预扣件数；无在途订单时返回空 Map
     */
    public Map<Long, Integer> countInflightOrderQtyByGift() {
        List<LocalMessage> inflightMessages = localMessageMapper.selectInflightOrderMessages();
        Map<Long, Integer> inflightByGift = new HashMap<>();
        if (inflightMessages == null || inflightMessages.isEmpty()) {
            return inflightByGift;
        }

        for (LocalMessage message : inflightMessages) {
            OrderCreateMessage payload;
            try {
                payload = JSON.parseObject(message.getPayload(), OrderCreateMessage.class);
            } catch (Exception e) {
                log.warn("[库存对账] 在途消息体解析失败，该条不计入在途预扣: messageId={}, err={}",
                        message.getMessageId(), e.getMessage());
                continue;
            }
            if (payload == null || payload.giftId() == null) {
                log.warn("[库存对账] 在途消息缺少 giftId，该条不计入在途预扣: messageId={}", message.getMessageId());
                continue;
            }
            int quantity = payload.quantity() != null && payload.quantity() > 0 ? payload.quantity() : 1;
            inflightByGift.merge(payload.giftId(), quantity, Integer::sum);
        }

        if (!inflightByGift.isEmpty()) {
            log.info("[库存对账] 在途预扣快照统计完成: {}", inflightByGift);
        }
        return inflightByGift;
    }
}
