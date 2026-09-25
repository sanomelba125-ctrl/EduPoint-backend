package com.campus.modules.mall.service;

import com.alibaba.fastjson2.JSON;
import com.campus.modules.mall.entity.Gift;
import com.campus.modules.mall.mapper.GiftMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 礼品缓存三防工程化落地服务 (P1-8 穿透/击穿/雪崩)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GiftCacheService {

    public static final String KEY_PREFIX_DETAIL = "gift:detail:";
    public static final String KEY_PREFIX_LOCK = "gift:lock:detail:";
    public static final String NULL_VALUE_PLACEHOLDER = "@@NULL_PLACEHOLDER@@";

    private final StringRedisTemplate redis;
    private final GiftMapper giftMapper;

    @Autowired(required = false)
    private RedissonClient redissonClient;

    private RBloomFilter<Long> giftBloomFilter;

    @PostConstruct
    public void init() {
        initBloomFilter();
    }

    /**
     * 1. 初始化布隆过滤器 (防穿透)
     * 预计容纳 10 万个礼品，误判率 0.01 (1%)
     */
    public synchronized void initBloomFilter() {
        if (redissonClient == null) {
            log.warn("[缓存三防-防穿透] RedissonClient 未注入，跳过 Redisson BloomFilter 初始化");
            return;
        }
        try {
            giftBloomFilter = redissonClient.getBloomFilter("bloom:gift:ids");
            giftBloomFilter.tryInit(100000L, 0.01);

            // 预热所有现存礼品 ID 进布隆过滤器
            List<Gift> gifts = giftMapper.selectList(null);
            if (gifts != null) {
                for (Gift g : gifts) {
                    if (g.getId() != null) {
                        giftBloomFilter.add(g.getId());
                    }
                }
                log.info("[缓存三防-防穿透] 布隆过滤器初始化完成，预加载礼品数: {}", gifts.size());
            }
        } catch (Exception e) {
            log.warn("[缓存三防-防穿透] 布隆过滤器初始化失败: {}", e.getMessage());
        }
    }

    /**
     * 新增礼品时动态加入布隆过滤器
     */
    public void addGiftToBloom(Long giftId) {
        if (giftBloomFilter != null && giftId != null) {
            try {
                giftBloomFilter.add(giftId);
            } catch (Exception e) {
                log.warn("[缓存三防] 布隆过滤器动态追加失败: giftId={}", giftId, e);
            }
        }
    }

    /**
     * 获取礼品详情 (缓存三防完整链路)
     */
    public Gift getGiftDetail(Long giftId) {
        if (giftId == null) return null;

        // 一、防穿透第一道防线：布隆过滤器前置拦截
        if (giftBloomFilter != null) {
            try {
                if (!giftBloomFilter.contains(giftId)) {
                    log.info("[缓存三防-防穿透] 布隆过滤器直接阻断不存在的礼品ID: {}", giftId);
                    return null;
                }
            } catch (Exception e) {
                // 布隆过滤器异常容错放行
                log.warn("[缓存三防-防穿透] 布隆过滤器判定异常，降级放行: giftId={}", giftId, e);
            }
        }

        String cacheKey = GiftRedisKeys.detail(giftId);

        // 二、读取 Redis 缓存
        String cachedJson = redis.opsForValue().get(cacheKey);
        if (cachedJson != null) {
            if (NULL_VALUE_PLACEHOLDER.equals(cachedJson)) {
                // 命中空值缓存，防反复穿透
                return null;
            }
            try {
                return JSON.parseObject(cachedJson, Gift.class);
            } catch (Exception e) {
                log.warn("[缓存解析] 缓存 JSON 格式异常，触发回源: key={}", cacheKey);
            }
        }

        // 三、防击穿第二道防线：互斥锁 (Mutex Key) 回源保护
        String lockKey = GiftRedisKeys.detailRebuildLock(giftId);
        Boolean acquired = redis.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(3));
        if (Boolean.TRUE.equals(acquired)) {
            try {
                // 双重检查缓存
                String recheck = redis.opsForValue().get(cacheKey);
                if (recheck != null) {
                    if (NULL_VALUE_PLACEHOLDER.equals(recheck)) return null;
                    return JSON.parseObject(recheck, Gift.class);
                }

                // 查库回源
                Gift dbGift = giftMapper.selectById(giftId);
                if (dbGift == null || (dbGift.getDeleted() != null && dbGift.getDeleted() == 1)) {
                    // 防穿透第二道防线：空值缓存兜底 (短 TTL: 60 秒)
                    redis.opsForValue().set(cacheKey, NULL_VALUE_PLACEHOLDER, Duration.ofSeconds(60));
                    return null;
                }

                // 四、防雪崩第三道防线：基准 TTL 叠加随机抖动 (+ 0~300秒)
                long baseTtlSeconds = 1800L; // 30 分钟
                long jitterSeconds = ThreadLocalRandom.current().nextInt(300); // 随机抖动
                long totalTtl = baseTtlSeconds + jitterSeconds;

                redis.opsForValue().set(cacheKey, JSON.toJSONString(dbGift), Duration.ofSeconds(totalTtl));
                return dbGift;
            } finally {
                redis.delete(lockKey);
            }
        } else {
            // 未获取到互斥锁，短暂休眠 50ms 后自旋从缓存重试
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            String retryCache = redis.opsForValue().get(cacheKey);
            if (retryCache != null && !NULL_VALUE_PLACEHOLDER.equals(retryCache)) {
                return JSON.parseObject(retryCache, Gift.class);
            }
            // 降级直接查库或返回空
            return giftMapper.selectById(giftId);
        }
    }
}
