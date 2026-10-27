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
import org.springframework.util.StringUtils;

import java.util.Arrays;
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
 * 4. Redis 与 MySQL 差异对账；
 * 5. MQ 补偿按 messageId 一次性幂等回补。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GiftStockRedisService {

    public static final String KEY_PREFIX_STOCK = "gift:stock:";
    public static final String KEY_PREFIX_DETAIL = "gift:detail:";
    public static final String KEY_PREFIX_COMPENSATED = "gift:stock:compensated:";
    private static final long COMPENSATE_DEDUPE_TTL_SECONDS = 7 * 24 * 3600L;

    private final StringRedisTemplate redis;
    private final GiftMapper giftMapper;

    private static final DefaultRedisScript<Long> DECR_SCRIPT;
    private static final DefaultRedisScript<Long> INCR_SCRIPT;
    private static final DefaultRedisScript<Long> RECONCILE_SCRIPT;
    private static final DefaultRedisScript<Long> COMPENSATE_ONCE_SCRIPT;
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

        RECONCILE_SCRIPT = new DefaultRedisScript<>();
        RECONCILE_SCRIPT.setLocation(new ClassPathResource("lua/gift_stock_reconcile.lua"));
        RECONCILE_SCRIPT.setResultType(Long.class);

        COMPENSATE_ONCE_SCRIPT = new DefaultRedisScript<>();
        COMPENSATE_ONCE_SCRIPT.setLocation(new ClassPathResource("lua/gift_stock_compensate_once.lua"));
        COMPENSATE_ONCE_SCRIPT.setResultType(Long.class);
    }

    /**
     * 库存主动预热（管理端创建/编辑礼品、库存被显式修改后调用）。
     *
     * <p>这是<b>允许覆盖</b>的写入口，仅在数据库库存确实发生变化时使用。
     * 秒杀链路的懒加载必须走 {@link #warmupStockIfAbsent(Long, int)}，否则会覆盖
     * 其他线程已经扣减过的在途库存。</p>
     */
    public void warmupStock(Long giftId, int stock) {
        String key = GiftRedisKeys.stock(giftId);
        redis.opsForValue().set(key, String.valueOf(stock));
        log.info("[库存预热] 成功将礼品库存预热至 Redis: giftId={}, stock={}", giftId, stock);
    }

    /**
     * 懒加载预热：仅当库存 key 不存在时写入数据库库存（P1 修复）。
     *
     * <p>旧实现直接 {@code SET}，两个请求同时冷启动时会互相覆盖：一方已把 10 扣到 9，
     * 另一方又把数据库里的 10 写回去再扣一次，结果两个成功请求只净减 1 件，直接超卖。
     * 这里改用 SETNX 语义，保证初始值只写一次，其余请求都在当前值上继续原子扣减。</p>
     *
     * @return true=本次写入成功；false=key 已存在，未覆盖
     */
    public boolean warmupStockIfAbsent(Long giftId, int stock) {
        String key = GiftRedisKeys.stock(giftId);
        Boolean created = redis.opsForValue().setIfAbsent(key, String.valueOf(stock));
        if (Boolean.TRUE.equals(created)) {
            log.info("[库存预热] 懒加载写入初始库存: giftId={}, stock={}", giftId, stock);
            return true;
        }
        log.info("[库存预热] 库存 key 已被其他请求写入，跳过覆盖: giftId={}", giftId);
        return false;
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
                // 只允许首个请求写入初始库存：并发冷启动时不得把别人已扣减的值覆盖回数据库旧值
                warmupStockIfAbsent(giftId, dbStock);

                // 在 key 的当前值上再次原子扣减；若其他请求已扣到 0，Lua 返回 0 而不是超扣
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
     * 非幂等反向回补，仅保留给历史非 MQ 调用兼容。
     *
     * @deprecated 所有 MQ 消费、Outbox 判死和事务回滚补偿必须使用
     * {@link #compensateStockOnce(Long, int, String)}，按 messageId 原子去重。
     */
    @Deprecated
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
     * 按 messageId 幂等回补库存（P0-4）。
     *
     * <p>同一消息无论因 ACK 失败、重复投递还是补偿重试被处理多少次，库存最多只加回一次。
     * 库存 key 不存在时返回 -1，绝不凭空 SET 一个库存值，避免把其他在途数据覆盖掉。</p>
     *
     * @return 1=本次已回补；0=该 messageId 已回补过；-1=库存 key 不存在；-2=执行异常
     */
    public int compensateStockOnce(Long giftId, int quantity, String messageId) {
        if (giftId == null || quantity <= 0 || !StringUtils.hasText(messageId)) {
            log.warn("[库存补偿] 参数非法，跳过一次性回补: giftId={}, quantity={}, messageId={}",
                    giftId, quantity, messageId);
            return -2;
        }

        String stockKey = GiftRedisKeys.stock(giftId);
        String dedupeKey = KEY_PREFIX_COMPENSATED + messageId.trim();
        try {
            Long result = redis.execute(
                    COMPENSATE_ONCE_SCRIPT,
                    Arrays.asList(stockKey, dedupeKey),
                    String.valueOf(quantity),
                    String.valueOf(COMPENSATE_DEDUPE_TTL_SECONDS)
            );
            if (result == null) {
                log.error("[库存补偿] Lua 返回 null: giftId={}, messageId={}", giftId, messageId);
                return -2;
            }
            if (result == 1L) {
                log.info("[库存补偿] 一次性回补成功: giftId={}, quantity={}, messageId={}",
                        giftId, quantity, messageId);
                return 1;
            }
            if (result == 0L) {
                log.info("[库存补偿] messageId 已回补过，幂等跳过: giftId={}, messageId={}", giftId, messageId);
                return 0;
            }
            log.warn("[库存补偿] 库存 key 不存在，跳过回补，交由对账处理: giftId={}, messageId={}",
                    giftId, messageId);
            return -1;
        } catch (Exception e) {
            log.error("[库存补偿] 一次性回补执行异常: giftId={}, messageId={}", giftId, messageId, e);
            return -2;
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
     * 差异对账：以「MySQL 实存 − 在途预扣」作为唯一正确可用量修正 Redis。
     *
     * <p><b>为什么不能直接用 MySQL 库存覆盖：</b>秒杀链路是「Redis 先原子预扣、MQ 异步落库」，
     * 在消息被消费落库之前，Redis 的可用库存<b>本来就应当</b>低于 MySQL 的实存库存，
     * 差额正是在途预扣。若按 MySQL 直接覆盖，会把在途预扣一并抹掉——Redis 凭空多出库存，
     * 下一秒就被重复预扣，形成超卖窗口（这正是「用户看到抢光却无人下单」的成因）。
     *
     * <p><b>修正策略（单向安全阀）：</b>
     * <ol>
     *   <li>Redis &gt; 应有可用量：库存高于「实存 − 在途」，存在超卖风险，<b>必须下修</b>；</li>
     *   <li>Redis &lt; 应有可用量：疑似少卖，但<b>不自动上修</b>。该差值同样可能是「预扣已发生、
     *       发件箱尚未提交」的瞬时状态，盲目上修会抹掉那笔在途预扣。少卖的所有已知成因
     *       （MQ 投递失败、毒消息、业务拒绝、消费异常）均已由 {@code OutboxRetryScheduler}
     *       与 {@code OrderCreateConsumer} 的反向补偿覆盖，此处只告警。</li>
     * </ol>
     *
     * <p><b>并发安全：</b>修正走 Lua CAS，仅当 Redis 当前值仍等于本次观测值时才写入，
     * 因此对账期间新发生的预扣不会被覆盖掉。
     *
     * @param giftId      礼品 ID
     * @param inflightQty 在途预扣件数（尚未落库的秒杀订单占用量，由发件箱统计得出；未知时传 0）
     */
    public void reconcileStock(Long giftId, int inflightQty) {
        Gift gift = giftMapper.selectById(giftId);
        if (gift == null) {
            return;
        }
        int dbStock = gift.getStock() != null ? gift.getStock() : 0;
        int inflight = Math.max(inflightQty, 0);
        int expectedStock = Math.max(0, dbStock - inflight);
        Integer redisStock = getStock(giftId);

        // 未预热：无需比对，按「实存 − 在途」预热，避免把在途量当成可用库存放出去
        if (redisStock == null) {
            warmupStock(giftId, expectedStock);
            log.info("[库存对账] Redis 未预热，按实存扣减在途后预热: giftId={}, dbStock={}, inflight={}, expected={}",
                    giftId, dbStock, inflight, expectedStock);
            return;
        }

        if (redisStock == expectedStock) {
            return;
        }

        // 少卖方向：不自动上修（见方法注释第 2 点），仅告警，交由上游反向补偿处理
        if (redisStock < expectedStock) {
            log.warn("[对账告警] Redis 可用库存低于「实存-在途」，疑似少卖，不自动上修以免抹掉在途预扣: "
                            + "giftId={}, redisStock={}, dbStock={}, inflight={}, expected={}",
                    giftId, redisStock, dbStock, inflight, expectedStock);
            return;
        }

        // 超卖方向：Redis 可用库存高于应有值，必须下修
        if (compareAndSetStock(giftId, redisStock, expectedStock)) {
            log.warn("[对账修复] Redis 可用库存高于「实存-在途」，存在超卖风险，已以 MySQL 为准下修: "
                            + "giftId={}, redisStock={}, dbStock={}, inflight={}, fixedTo={}",
                    giftId, redisStock, dbStock, inflight, expectedStock);
        } else {
            log.info("[对账跳过] 修正前检测到并发库存变动，本轮放弃，交由下一轮对账: giftId={}", giftId);
        }
    }

    /**
     * 原子「比较并修正」库存：仅当 Redis 当前值仍等于 {@code expectStock} 时才写入 {@code targetStock}。
     *
     * @return true-已修正；false-观测值已被并发修改（期间发生新的预扣/回补），本次未修正
     */
    private boolean compareAndSetStock(Long giftId, int expectStock, int targetStock) {
        String key = GiftRedisKeys.stock(giftId);
        try {
            Long result = redis.execute(RECONCILE_SCRIPT, Collections.singletonList(key),
                    String.valueOf(expectStock), String.valueOf(targetStock));
            return result != null && result == 1L;
        } catch (Exception e) {
            log.warn("[对账修复] CAS 修正 Redis 库存失败: giftId={}, expect={}, target={}",
                    giftId, expectStock, targetStock, e);
            return false;
        }
    }
}
