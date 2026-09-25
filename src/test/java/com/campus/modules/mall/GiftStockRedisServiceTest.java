package com.campus.modules.mall;

import com.campus.modules.mall.entity.Gift;
import com.campus.modules.mall.mapper.GiftMapper;
import com.campus.modules.mall.service.GiftStockRedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 礼品库存 Redis Lua 预扣与一致性服务单测 (P1-1 & P1-5)
 */
class GiftStockRedisServiceTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOperations;
    private GiftMapper giftMapper;
    private GiftStockRedisService giftStockRedisService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        giftMapper = mock(GiftMapper.class);

        when(redis.opsForValue()).thenReturn(valueOperations);
        giftStockRedisService = new GiftStockRedisService(redis, giftMapper);
    }

    @Test
    @DisplayName("测试库存主动预热：设置 Redis Key 为初始库存")
    void testWarmupStock() {
        giftStockRedisService.warmupStock(6L, 10);
        verify(valueOperations).set("gift:stock:6", "10");
    }

    @Test
    @DisplayName("测试 Redis Lua 原子预扣成功：脚本返回 1 判定预扣成功")
    @SuppressWarnings("unchecked")
    void testPreDeductStock_Success() {
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);

        boolean result = giftStockRedisService.preDeductStock(6L, 1);
        assertTrue(result);
    }

    @Test
    @DisplayName("测试 Redis Lua 原子预扣库存不足：脚本返回 0 内存层快速阻断")
    @SuppressWarnings("unchecked")
    void testPreDeductStock_StockOut_FastFail() {
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(0L);

        boolean result = giftStockRedisService.preDeductStock(6L, 1);
        assertFalse(result);
    }

    @Test
    @DisplayName("测试未预热懒加载：脚本返回 -1 时从 MySQL 查库预热并再次预扣")
    @SuppressWarnings("unchecked")
    void testPreDeductStock_LazyWarmup() {
        Gift gift = new Gift();
        gift.setId(6L);
        gift.setStock(15);
        gift.setDeleted(0);
        when(giftMapper.selectById(6L)).thenReturn(gift);

        // 第一次返回 -1 (未预热)，第二次返回 1 (预扣成功)
        when(redis.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(-1L)
                .thenReturn(1L);

        boolean result = giftStockRedisService.preDeductStock(6L, 1);
        assertTrue(result);
        verify(valueOperations).set("gift:stock:6", "15");
    }

    @Test
    @DisplayName("测试库存反向回补补偿：执行 INCRBY Lua 脚本")
    @SuppressWarnings("unchecked")
    void testCompensateStock() {
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(10L);

        giftStockRedisService.compensateStock(6L, 1);
        verify(redis).execute(any(RedisScript.class), anyList(), eq("1"));
    }

    @Test
    @DisplayName("测试定时对账巡检：当 Redis 与 MySQL 不一致时以 MySQL 为准修正")
    void testReconcileStock_Inconsistent() {
        Gift gift = new Gift();
        gift.setId(6L);
        gift.setStock(8); // MySQL 真实库存为 8
        when(giftMapper.selectById(6L)).thenReturn(gift);

        when(valueOperations.get("gift:stock:6")).thenReturn("10"); // Redis 脏库存为 10

        giftStockRedisService.reconcileStock(6L);

        // 验证修正为 8
        verify(valueOperations).set("gift:stock:6", "8");
    }
}
