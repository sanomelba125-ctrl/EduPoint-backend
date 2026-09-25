package com.campus.modules.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.modules.mall.dto.GiftExchangeTokenVO;
import com.campus.modules.mall.entity.StudentGift;
import com.campus.modules.mall.mapper.StudentGiftMapper;
import com.campus.modules.mall.service.GiftIdempotentService;
import com.campus.modules.mall.service.GiftRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

/**
 * 礼品兑换幂等与防重令牌专职服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GiftIdempotentServiceImpl implements GiftIdempotentService {

    private final StringRedisTemplate redis;
    private final StudentGiftMapper studentGiftMapper;

    /** 防重兑换令牌键前缀：gift:exchange:token:{studentId}:{giftId} */
    public static final String EXCHANGE_TOKEN_PREFIX = "gift:exchange:token:";

    /** 并发防重占位键前缀：gift:exchange:idem:{studentId}:{requestId} */
    public static final String IDEMPOTENT_KEY_PREFIX = "gift:exchange:idem:";

    /** 防重令牌有效期：10 分钟 */
    public static final Duration TOKEN_TTL = Duration.ofMinutes(10);

    /** 占位处理中状态锁持有最长时长：30 秒 (防处理中进程崩溃死锁) */
    public static final Duration IDEMPOTENT_PENDING_TTL = Duration.ofSeconds(30);

    /** 占位处理完成结果保留时长：24 小时 */
    public static final Duration IDEMPOTENT_DONE_TTL = Duration.ofHours(24);

    public static final String IDEMPOTENT_PENDING = "PENDING";
    public static final String IDEMPOTENT_DONE_PREFIX = "DONE:";

    /**
     * Lua 脚本：原子核验并删除防重 Token (核验即焚，Token 桶一次性机制)
     * 若 key 对应的值与传入 token 匹配，则立即删除并返回 1；否则返回 0
     */
    private static final RedisScript<Long> CHECK_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end",
            Long.class
    );

    @Override
    public GiftExchangeTokenVO generateExchangeToken(Long studentId, Long giftId) {
        if (studentId == null || giftId == null) {
            throw new IllegalArgumentException("生成防重令牌时 studentId 与 giftId 均不能为空");
        }
        String token = "EXT-" + UUID.randomUUID().toString().replace("-", "");
        String key = buildTokenKey(studentId, giftId);

        try {
            redis.opsForValue().set(key, token, TOKEN_TTL);
            log.info("已生成并下发礼品兑换防重令牌: studentId={}, giftId={}, token={}, ttl={}s",
                    studentId, giftId, token, TOKEN_TTL.toSeconds());
        } catch (Exception e) {
            log.warn("保存兑换防重令牌到 Redis 异常: key={}, error={}", key, e.getMessage());
        }

        return GiftExchangeTokenVO.builder()
                .token(token)
                .giftId(giftId)
                .expireSeconds(TOKEN_TTL.toSeconds())
                .createTime(LocalDateTime.now())
                .build();
    }

    @Override
    public boolean verifyAndConsumeToken(Long studentId, Long giftId, String token) {
        if (studentId == null || giftId == null || !StringUtils.hasText(token)) {
            return false;
        }
        String key = buildTokenKey(studentId, giftId);
        try {
            Long result = redis.execute(CHECK_AND_DELETE_SCRIPT, Collections.singletonList(key), token.trim());
            boolean success = result != null && result == 1L;
            if (success) {
                log.info("防重令牌核验通过并成功原子销毁: key={}, token={}", key, token);
            } else {
                log.warn("防重令牌核验失败(已失效或不匹配): key={}, token={}, luaResult={}", key, token, result);
            }
            return success;
        } catch (Exception e) {
            log.warn("Lua 脚本执行核验防重 Token 异常，降级放行(由 DB 联合唯一索引兜底): key={}, error={}", key, e.getMessage());
            return true;
        }
    }

    @Override
    public boolean tryAcquireSlot(Long studentId, String requestId) {
        if (studentId == null || !StringUtils.hasText(requestId)) {
            return true;
        }
        String key = buildIdempotentKey(studentId, requestId);
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, IDEMPOTENT_PENDING, IDEMPOTENT_PENDING_TTL));
        } catch (Exception e) {
            log.warn("兑换幂等占位写入 Redis 失败，降级放行: key={}, error={}", key, e.getMessage());
            return true;
        }
    }

    @Override
    public void markSlotDone(Long studentId, String requestId, Long orderId) {
        if (studentId == null || !StringUtils.hasText(requestId)) {
            return;
        }
        String key = buildIdempotentKey(studentId, requestId);
        try {
            redis.opsForValue().set(key, IDEMPOTENT_DONE_PREFIX + orderId, IDEMPOTENT_DONE_TTL);
            log.info("兑换幂等占位已落定完成: key={}, orderId={}", key, orderId);
        } catch (Exception e) {
            log.warn("兑换幂等占位标记完成失败(不影响订单): key={}, error={}", key, e.getMessage());
        }
    }

    @Override
    public void releaseSlotOnRollback(Long studentId, String requestId) {
        if (studentId == null || !StringUtils.hasText(requestId)) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        String key = buildIdempotentKey(studentId, requestId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    try {
                        redis.delete(key);
                        log.info("兑换事务非提交结束(状态={})，已释放幂等占位允许重试: key={}", status, key);
                    } catch (Exception e) {
                        log.warn("兑换事务回滚释放幂等占位异常(将由自然 TTL 过期): key={}, error={}", key, e.getMessage());
                    }
                }
            }
        });
    }

    @Override
    public StudentGift findExistingOrder(Long studentId, String requestId) {
        if (studentId == null || !StringUtils.hasText(requestId)) {
            return null;
        }
        return studentGiftMapper.selectOne(new LambdaQueryWrapper<StudentGift>()
                .eq(StudentGift::getStudentId, studentId)
                .eq(StudentGift::getRequestId, requestId.trim()), false);
    }

    private String buildTokenKey(Long studentId, Long giftId) {
        return GiftRedisKeys.exchangeToken(studentId, giftId);
    }

    private String buildIdempotentKey(Long studentId, String requestId) {
        return GiftRedisKeys.exchangeIdempotent(studentId, requestId);
    }
}
