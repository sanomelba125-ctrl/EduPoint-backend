package com.campus.modules.mall.service;

import com.campus.common.exception.BusinessException;
import com.campus.modules.mall.entity.Gift;
import com.campus.modules.mall.mapper.GiftMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 礼品库存 Redis 原子预扣与缓存一致性服务 (P1-1 & P1-5)
 * 涵盖：
 * 1. Redis + Lua 原子预扣库存与回滚；
 * 2. 拒绝 Fail-Open 的安全快速失败机制；
 * 3. Cache-Aside 延时双删；
 * 4. Redis 与 MySQL 差异对账。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GiftStockRedisService {

    public static final String KEY_PREFIX_STOCK = "gift:stock:";
    public static final String KEY_PREFIX_DETAIL = "gift:detail:";

    private final StringRedisTemplate redis;
    private final GiftMapper giftMapper;

    private static final DefaultRedisScript<Long> DECR_SCRIPT;
    private static final DefaultRedisScript<Long> INCR_SCRIPT;
    private static final ScheduledExecutorService DOUBLE_DELETE_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "double-delete-thread");
                t.setDaemon(true);
                return t;
            });

    static {
        DECR_SCRIPT = new DefaultRedisScript<>();
        DECR_SCRIPT.setLocation(new ClassPathResource("lua/gift_stock_decr.lua"));
        DECR_SCRIPT.setResultType(Long.class);

        INCR_SCRIPT = new DefaultRedisScript<>();
        INCR_SCRIPT.setLocation(new ClassPathResource("lua/gift_stock_incr.lua"));
        INCR_SCRIPT.setResultType(Long.class);
    }

    /**
     * 库存主动预热
     */
    public void warmupStock(Long giftId, int stock) {
        String key = GiftRedisKeys.stock(giftId);
        redis.opsForValue().set(key, String.valueOf(stock));
        log.info("[库存预热] 成功将礼品库存预热至 Redis: giftId={}, stock={}", giftId, stock);
    }

    /**
     * Redis + Lua 原子预扣库存 (P1-1)
     * @param giftId 礼品ID
     * @param quantity 扣减件数
     * @return true-预扣成功, false-库存不足
     */
    public boolean preDeductStock(Long giftId, int quantity) {
        String key = GiftRedisKeys.stock(giftId);
        try {
            Long result = redis.execute(DECR_SCRIPT, Collections.singletonList(key), String.valueOf(quantity));
            if (result == null) {
                log.error("[Redis预扣] 脚本返回 null, 执行 Fail-Close 快速失败: giftId={}", giftId);
                throw new BusinessException("秒杀库存服务繁忙，请稍后重试！");
            }

            if (result == 1L) {
                // 扣减成功
                return true;
            } else if (result == 0L) {
                // 明确库存不足，在内存层阻断 99% 的无效流量
                log.info("[Redis预扣] 礼品库存不足，在内存层快速阻断: giftId={}, requestedQty={}", giftId, quantity);
                return false;
            } else if (result == -1L) {
                // 缓存未预热，走懒加载从数据库同步预热
                log.warn("[Redis预扣] 库存 Key 尚未预热，尝试从 MySQL 懒加载补全: giftId={}", giftId);
                Gift gift = giftMapper.selectById(giftId);
                if (gift == null || (gift.getDeleted() != null && gift.getDeleted() == 1)) {
                    throw new BusinessException("您选择的礼品不存在");
                }
                int dbStock = (gift.getStock() != null && gift.getStock() > 0) ? gift.getStock() : 0;
                warmupStock(giftId, dbStock);

                // 预热后再次原子扣减
                Long retryResult = redis.execute(DECR_SCRIPT, Collections.singletonList(key), String.valueOf(quantity));
                return retryResult != null && retryResult == 1L;
            }
            return false;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Redis预扣] Redis 发生异常，绝不 Fail-Open 放行敲打 MySQL: giftId={}", giftId, e);
            throw new BusinessException("秒杀通道繁忙，请稍候重试！");
        }
    }

    /**
     * 反向回补补偿库存 (P1-1 & P1-5)
     */
    public void compensateStock(Long giftId, int quantity) {
        String key = GiftRedisKeys.stock(giftId);
        try {
            Long newStock = redis.execute(INCR_SCRIPT, Collections.singletonList(key), String.valueOf(quantity));
            log.info("[库存补偿] 成功回补 Redis 库存: giftId={}, 回补数量={}, 回补后库存={}", giftId, quantity, newStock);
        } catch (Exception e) {
            log.error("[库存补偿] 回补 Redis 库存失败: giftId={}, quantity={}", giftId, quantity, e);
        }
    }

    /**
     * 读取 Redis 当前剩余库存
     */
    public Integer getStock(Long giftId) {
        String key = GiftRedisKeys.stock(giftId);
        try {
            String val = redis.opsForValue().get(key);
            return val != null ? Integer.parseInt(val) : null;
        } catch (Exception e) {
            log.warn("[读取库存] Redis 异常: giftId={}", giftId, e);
            return null;
        }
    }

    /**
     * Cache-Aside 延时双删机制 (P1-5)
     * 管理员修改礼品信息或库存时调用:
     * 1. 立即删除 Redis 详情缓存;
     * 2. 异步延迟 500ms 再次删除 Redis 详情缓存，杜绝并发读回填脏数据。
     */
    public void evictGiftCacheWithDoubleDelete(Long giftId) {
        String cacheKey = GiftRedisKeys.detail(giftId);
        try {
            // 第一次删除
            redis.delete(cacheKey);
            log.info("[延时双删] 第一次删除缓存成功: key={}", cacheKey);

            // 异步延迟 500ms 执行第二次删除
            DOUBLE_DELETE_EXECUTOR.schedule(() -> {
                try {
                    redis.delete(cacheKey);
                    log.info("[延时双删] 延迟 500ms 第二次删除缓存成功: key={}", cacheKey);
                } catch (Exception ex) {
                    log.warn("[延时双删] 第二次删除缓存失败: key={}", cacheKey, ex);
                }
            }, 500, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("[延时双删] 执行第一次删除缓存异常: key={}", cacheKey, e);
        }
    }

    /**
     * 差异对账 (以 MySQL 为唯一真相源修正 Redis)
     */
    public void reconcileStock(Long giftId) {
        Gift gift = giftMapper.selectById(giftId);
        if (gift == null) return;
        Integer dbStock = gift.getStock() != null ? gift.getStock() : 0;
        Integer redisStock = getStock(giftId);

        if (redisStock == null || !redisStock.equals(dbStock)) {
            log.warn("[对账报警] 发现 Redis 与 MySQL 库存不一致！giftId={}, redisStock={}, dbStock={} -> 以 MySQL 为准自动修复",
                    giftId, redisStock, dbStock);
            warmupStock(giftId, dbStock);
        }
    }
}
