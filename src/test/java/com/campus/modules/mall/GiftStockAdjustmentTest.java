package com.campus.modules.mall;

import com.campus.common.exception.BusinessException;
import com.campus.modules.mall.dto.GiftVO;
import com.campus.modules.mall.entity.Gift;
import com.campus.modules.mall.mapper.GiftMapper;
import com.campus.modules.mall.mapper.StudentGiftMapper;
import com.campus.modules.mall.service.GiftStockRedisService;
import com.campus.modules.mall.service.LocalMessageService;
import com.campus.modules.mall.service.impl.GiftServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 礼品改库存（盘盈入库 / 盘亏核减）条件更新单元测试
 */
class GiftStockAdjustmentTest {

    private GiftMapper giftMapper;
    private StudentGiftMapper studentGiftMapper;
    private GiftServiceImpl giftService;

    @BeforeEach
    void setUp() {
        giftMapper = mock(GiftMapper.class);
        studentGiftMapper = mock(StudentGiftMapper.class);
        giftService = new GiftServiceImpl(studentGiftMapper);
        ReflectionTestUtils.setField(giftService, "baseMapper", giftMapper);
    }

    @Test
    @DisplayName("测试调整数量为 0 时应抛出参数异常")
    void testAdjustStock_ZeroQuantity_ShouldThrowException() {
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            giftService.adjustStock(1L, 0, 100L);
        });
        assertTrue(ex.getMessage().contains("不能为 0"));
    }

    @Test
    @DisplayName("测试目标礼品不存在时调整库存应抛出异常")
    void testAdjustStock_GiftNotFound_ShouldThrowException() {
        when(giftMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            giftService.adjustStock(999L, 10, 100L);
        });
        assertTrue(ex.getMessage().contains("不存在"));
    }

    @Test
    @DisplayName("测试正数调整（盘盈入库）：调用 restock 条件更新并成功累加")
    void testAdjustStock_Positive_ShouldCallRestock() {
        Gift gift = new Gift();
        gift.setId(10L);
        gift.setName("定制纪念徽章");
        gift.setStock(20);
        gift.setTotalStock(50);
        when(giftMapper.selectById(10L)).thenReturn(gift);
        when(giftMapper.restock(eq(10L), eq(15), eq(100L))).thenReturn(1);

        GiftVO vo = giftService.adjustStock(10L, 15, 100L);

        assertNotNull(vo);
        assertEquals(10L, vo.getId());
        verify(giftMapper, times(1)).restock(10L, 15, 100L);
        verify(giftMapper, never()).deductStock(any(), any(), any());
    }

    @Test
    @DisplayName("测试负数调整（盘亏核减）：库存充裕时调用 deductStock 条件 SQL 成功核减")
    void testAdjustStock_Negative_SufficientStock_ShouldCallDeductStock() {
        Gift gift = new Gift();
        gift.setId(10L);
        gift.setName("定制纪念徽章");
        gift.setStock(20);
        gift.setTotalStock(50);
        when(giftMapper.selectById(10L)).thenReturn(gift);
        when(giftMapper.deductStock(eq(10L), eq(5), eq(100L))).thenReturn(1);

        GiftVO vo = giftService.adjustStock(10L, -5, 100L);

        assertNotNull(vo);
        assertEquals(10L, vo.getId());
        verify(giftMapper, times(1)).deductStock(10L, 5, 100L);
        verify(giftMapper, never()).restock(any(), any(), any());
    }

    @Test
    @DisplayName("测试负数调整（盘亏核减）：可用库存不足时条件 SQL 返回 0 应抛出友好业务异常")
    void testAdjustStock_Negative_InsufficientStock_ShouldThrowException() {
        Gift gift = new Gift();
        gift.setId(10L);
        gift.setName("定制纪念徽章");
        gift.setStock(3);
        gift.setTotalStock(50);
        when(giftMapper.selectById(10L)).thenReturn(gift);
        // 条件 SQL: WHERE stock >= 5 失败，返回 0 行
        when(giftMapper.deductStock(eq(10L), eq(5), eq(100L))).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            giftService.adjustStock(10L, -5, 100L);
        });

        assertTrue(ex.getMessage().contains("可用库存（3件）不足"));
        verify(giftMapper, times(1)).deductStock(10L, 5, 100L);
    }

    @Test
    @DisplayName("F3: 管理端补货使用增量 delta 调整 Redis，绝不使用 MySQL 绝对库存覆盖以保护在途预扣")
    void testAdjustStock_SyncRedisWithDelta() {
        GiftStockRedisService redisService = mock(GiftStockRedisService.class);
        ReflectionTestUtils.setField(giftService, "giftStockRedisService", redisService);

        Gift gift = new Gift();
        gift.setId(10L);
        gift.setName("定制纪念徽章");
        gift.setStock(20);
        gift.setTotalStock(50);

        Gift fresh = new Gift();
        fresh.setId(10L);
        fresh.setName("定制纪念徽章");
        fresh.setStock(35);
        fresh.setTotalStock(65);

        when(giftMapper.selectById(10L)).thenReturn(gift).thenReturn(fresh);
        when(giftMapper.restock(eq(10L), eq(15), eq(100L))).thenReturn(1);

        GiftVO vo = giftService.adjustStock(10L, 15, 100L);

        assertNotNull(vo);
        verify(redisService, times(1)).adjustStockDelta(10L, 15, 35);
        verify(redisService, never()).warmupStock(anyLong(), anyInt());
        verify(redisService, times(1)).evictGiftCacheWithDoubleDelete(10L);
    }

    @Test
    @DisplayName("F3 并发边界: 负向核减库存时校准秒杀在途单，核减超过非在途可用库存必须被阻断")
    void testAdjustStock_Negative_BlockedByInflightSeckill() {
        LocalMessageService localMessageService = mock(LocalMessageService.class);
        GiftStockRedisService redisService = mock(GiftStockRedisService.class);
        ReflectionTestUtils.setField(giftService, "localMessageService", localMessageService);
        ReflectionTestUtils.setField(giftService, "giftStockRedisService", redisService);

        Gift gift = new Gift();
        gift.setId(10L);
        gift.setName("定制纪念徽章");
        gift.setStock(10); // MySQL = 10
        gift.setTotalStock(50);
        when(giftMapper.selectById(10L)).thenReturn(gift);

        // 发件箱统计在途 8 单（未消费落库）
        when(localMessageService.countInflightOrderQtyByGift()).thenReturn(java.util.Map.of(10L, 8));
        when(redisService.getStock(10L)).thenReturn(2); // Redis = 2 (10 - 8)

        // 管理员试图核减 -5 件（超过非在途可用 2 件）
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            giftService.adjustStock(10L, -5, 100L);
        });

        assertTrue(ex.getMessage().contains("秒杀在途占用 8 件"));
        assertTrue(ex.getMessage().contains("可核减库存仅剩 2 件"));
        // 验证绝不执行数据库扣减
        verify(giftMapper, never()).deductStock(any(), any(), any());
    }

    @Test
    @DisplayName("F3 并发边界: 负向核减未侵占在途库存时允许核减并以增量 delta 同步 Redis")
    void testAdjustStock_Negative_AllowedWithinInflightMargin() {
        LocalMessageService localMessageService = mock(LocalMessageService.class);
        GiftStockRedisService redisService = mock(GiftStockRedisService.class);
        ReflectionTestUtils.setField(giftService, "localMessageService", localMessageService);
        ReflectionTestUtils.setField(giftService, "giftStockRedisService", redisService);

        Gift gift = new Gift();
        gift.setId(10L);
        gift.setName("定制纪念徽章");
        gift.setStock(10); // MySQL = 10
        gift.setTotalStock(50);

        Gift fresh = new Gift();
        fresh.setId(10L);
        fresh.setName("定制纪念徽章");
        fresh.setStock(8); // MySQL 10 - 2 = 8
        fresh.setTotalStock(48);

        when(giftMapper.selectById(10L)).thenReturn(gift).thenReturn(fresh);
        when(giftMapper.deductStock(10L, 2, 100L)).thenReturn(1);

        // 发件箱统计在途 3 单，可核减 10 - 3 = 7 件
        when(localMessageService.countInflightOrderQtyByGift()).thenReturn(java.util.Map.of(10L, 3));
        when(redisService.getStock(10L)).thenReturn(7);

        // 管理员核减 -2 件 (2 <= 7)
        GiftVO vo = giftService.adjustStock(10L, -2, 100L);

        assertNotNull(vo);
        verify(giftMapper, times(1)).deductStock(10L, 2, 100L);
        verify(redisService, times(1)).adjustStockDelta(10L, -2, 8);
        verify(redisService, times(1)).evictGiftCacheWithDoubleDelete(10L);
    }

    @Test
    @DisplayName("F3: deleteGift 无历史兑换订单时物理删除并触发缓存双删")
    void testDeleteGift_WithoutOrders_PhysicalDelete() {
        GiftStockRedisService redisService = mock(GiftStockRedisService.class);
        ReflectionTestUtils.setField(giftService, "giftStockRedisService", redisService);

        Gift gift = new Gift();
        gift.setId(10L);
        gift.setName("定制纪念徽章");
        when(giftMapper.selectById(10L)).thenReturn(gift);
        when(studentGiftMapper.selectCount(any())).thenReturn(0L);
        when(giftMapper.deleteById(10L)).thenReturn(1);

        String result = giftService.deleteGift(10L);

        assertEquals("删除成功", result);
        verify(giftMapper, times(1)).deleteById(10L);
        verify(redisService, times(1)).evictGiftCacheWithDoubleDelete(10L);
    }

    @Test
    @DisplayName("F3: deleteGift 存在历史兑换订单时自动降级下架并触发缓存双删")
    void testDeleteGift_WithOrders_OffShelfDowngrade() {
        GiftStockRedisService redisService = mock(GiftStockRedisService.class);
        ReflectionTestUtils.setField(giftService, "giftStockRedisService", redisService);

        Gift gift = new Gift();
        gift.setId(10L);
        gift.setName("定制纪念徽章");
        gift.setStatus(1);
        when(giftMapper.selectById(10L)).thenReturn(gift);
        when(studentGiftMapper.selectCount(any())).thenReturn(5L);
        when(giftMapper.updateById(any(Gift.class))).thenReturn(1);

        String result = giftService.deleteGift(10L);

        assertTrue(result.contains("已自动转为下架状态"));
        verify(giftMapper, never()).deleteById(any(Long.class));
        verify(giftMapper, times(1)).updateById(any(Gift.class));
        verify(redisService, times(1)).evictGiftCacheWithDoubleDelete(10L);
    }

    @Test
    @DisplayName("F3: toggleStatus 上下架变更触发缓存双删")
    void testToggleStatus_ShouldTriggerDoubleDelete() {
        GiftStockRedisService redisService = mock(GiftStockRedisService.class);
        ReflectionTestUtils.setField(giftService, "giftStockRedisService", redisService);

        Gift gift = new Gift();
        gift.setId(10L);
        gift.setName("定制纪念徽章");
        gift.setStatus(1);
        when(giftMapper.selectById(10L)).thenReturn(gift);
        when(giftMapper.updateById(any(Gift.class))).thenReturn(1);

        giftService.toggleStatus(10L, 0);

        verify(giftMapper, times(1)).updateById(any(Gift.class));
        verify(redisService, times(1)).evictGiftCacheWithDoubleDelete(10L);
    }
}
