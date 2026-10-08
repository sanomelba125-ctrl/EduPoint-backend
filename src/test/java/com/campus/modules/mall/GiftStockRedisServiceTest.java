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

    // ---------------------- 库存对账（P1-5：在途预扣感知 + 单向安全阀） ----------------------

    /** 构造礼品存根：giftId 对应 MySQL 实存库存 dbStock */
    private void stubGift(long giftId, int dbStock) {
        Gift gift = new Gift();
        gift.setId(giftId);
        gift.setStock(dbStock);
        when(giftMapper.selectById(giftId)).thenReturn(gift);
    }

    @Test
    @DisplayName("对账-超卖方向：Redis 高于「实存-在途」时必须下修，且走 Lua CAS 而非直接 SET")
    @SuppressWarnings("unchecked")
    void testReconcileStock_OverSellRisk_FixedDownByCas() {
        stubGift(6L, 8);                                            // MySQL 实存 8，无在途 → 应有可用 8
        when(valueOperations.get("gift:stock:6")).thenReturn("10");  // Redis 脏值 10
        when(redis.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(1L);

        giftStockRedisService.reconcileStock(6L, 0);

        // CAS：观测值 10 -> 目标值 8
        verify(redis).execute(any(RedisScript.class), anyList(), eq("10"), eq("8"));
    }

    @Test
    @DisplayName("对账-在途保护：差异可被在途预扣完整解释时不作任何修正")
    @SuppressWarnings("unchecked")
    void testReconcileStock_ExplainedByInflight_NoRepair() {
        stubGift(6L, 100);                                          // 实存 100，在途 5 → 应有可用 95
        when(valueOperations.get("gift:stock:6")).thenReturn("95");

        giftStockRedisService.reconcileStock(6L, 5);

        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(), any());
        verify(valueOperations, never()).set(anyString(), anyString());
    }

    @Test
    @DisplayName("对账-在途叠加超卖：修正目标必须是「实存-在途」而非实存，否则在途量会被重复放出")
    @SuppressWarnings("unchecked")
    void testReconcileStock_OverSellWithInflight_TargetExcludesInflight() {
        stubGift(6L, 100);                                          // 实存 100，在途 5 → 应有可用 95
        when(valueOperations.get("gift:stock:6")).thenReturn("105"); // Redis 异常偏高
        when(redis.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(1L);

        giftStockRedisService.reconcileStock(6L, 5);

        verify(redis).execute(any(RedisScript.class), anyList(), eq("105"), eq("95"));
    }

    @Test
    @DisplayName("对账-少卖方向：Redis 低于应有可用量时只告警不自动上修（避免抹掉在途预扣）")
    @SuppressWarnings("unchecked")
    void testReconcileStock_UnderSell_NoAutoUpFix() {
        stubGift(6L, 100);                                          // 实存 100，无在途 → 应有可用 100
        when(valueOperations.get("gift:stock:6")).thenReturn("95");

        giftStockRedisService.reconcileStock(6L, 0);

        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(), any());
        verify(valueOperations, never()).set(anyString(), anyString());
    }

    @Test
    @DisplayName("对账-未预热且存在在途：按「实存-在途」预热，不把在途量当可用库存放出去")
    void testReconcileStock_UnwarmedWithInflight_WarmupExcludesInflight() {
        stubGift(6L, 100);

        giftStockRedisService.reconcileStock(6L, 5);

        verify(valueOperations).set("gift:stock:6", "95");
    }

    @Test
    @DisplayName("对账-并发竞争：CAS 未命中（观测值已被新预扣改动）时放弃本轮修正")
    @SuppressWarnings("unchecked")
    void testReconcileStock_CasMiss_SkipsRepair() {
        stubGift(6L, 8);
        when(valueOperations.get("gift:stock:6")).thenReturn("10");
        when(redis.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(0L); // CAS 未命中

        giftStockRedisService.reconcileStock(6L, 0);

        verify(valueOperations, never()).set(anyString(), anyString());
    }
}
