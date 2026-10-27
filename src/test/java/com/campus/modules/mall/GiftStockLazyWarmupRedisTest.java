package com.campus.modules.mall;

import com.campus.modules.mall.entity.Gift;
import com.campus.modules.mall.mapper.GiftMapper;
import com.campus.modules.mall.service.GiftStockRedisService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P1 真实 Redis 验证：并发冷启动不得覆盖库存。
 *
 * <p>默认跳过，避免 CI 依赖外部 Redis。本地验证方式：</p>
 * <pre>
 *   RUN_REDIS_IT=true TEST_REDIS_PASSWORD=xxx ./mvnw test -Dtest=GiftStockLazyWarmupRedisTest
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "RUN_REDIS_IT", matches = "true")
class GiftStockLazyWarmupRedisTest {

    private static final long GIFT_ID = 990001L;
    private static final String STOCK_KEY = "gift:stock:" + GIFT_ID;
    private static final int DB_STOCK = 10;

    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private GiftStockRedisService service;

    @BeforeEach
    void setUp() {
        String host = System.getenv().getOrDefault("TEST_REDIS_HOST", "127.0.0.1");
        int port = Integer.parseInt(System.getenv().getOrDefault("TEST_REDIS_PORT", "6379"));
        String password = System.getenv("TEST_REDIS_PASSWORD");

        RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration(host, port);
        if (password != null && !password.isBlank()) {
            cfg.setPassword(RedisPassword.of(password));
        }
        factory = new LettuceConnectionFactory(cfg);
        factory.afterPropertiesSet();

        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();

        GiftMapper giftMapper = mock(GiftMapper.class);
        Gift gift = new Gift();
        gift.setId(GIFT_ID);
        gift.setStock(DB_STOCK);
        gift.setDeleted(0);
        when(giftMapper.selectById(GIFT_ID)).thenReturn(gift);

        service = new GiftStockRedisService(redis, giftMapper);
        redis.delete(STOCK_KEY);
    }

    @AfterEach
    void tearDown() {
        if (redis != null) {
            redis.delete(STOCK_KEY);
        }
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    @DisplayName("SETNX 预热不覆盖已有库存：已是 9 时不会被写回数据库值 10")
    void warmupIfAbsentMustNotOverwrite() {
        redis.opsForValue().set(STOCK_KEY, "9");

        assertFalse(service.warmupStockIfAbsent(GIFT_ID, DB_STOCK), "key 已存在时必须返回 false");
        assertEquals("9", redis.opsForValue().get(STOCK_KEY), "已有库存不得被覆盖");
    }

    @Test
    @DisplayName("20 线程并发冷启动抢 10 件，连续 5 轮：成功数必须恰为 10，库存必须归零")
    void concurrentColdStartMustNotOversell() throws Exception {
        int threads = 20;
        int rounds = 5;

        for (int round = 1; round <= rounds; round++) {
            redis.delete(STOCK_KEY);

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);
            AtomicInteger success = new AtomicInteger();
            AtomicInteger errors = new AtomicInteger();

            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        if (service.preDeductStock(GIFT_ID, 1)) {
                            success.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS), "线程未全部就绪");
            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS), "并发扣减未在 30 秒内完成");
            pool.shutdownNow();

            String left = redis.opsForValue().get(STOCK_KEY);
            assertEquals(0, errors.get(), "第 " + round + " 轮出现异常，冷启动不应 Fail-Close 报错");
            assertEquals(DB_STOCK, success.get(), "第 " + round + " 轮超卖：成功数必须等于库存 10");
            assertEquals("0", left, "第 " + round + " 轮库存必须恰好归零，不能被覆盖回旧值");
        }
    }
}
