package com.campus.modules.mall.service;

import com.alibaba.fastjson2.JSON;
import com.campus.modules.mall.entity.LocalMessage;
import com.campus.modules.mall.mapper.LocalMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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
}
