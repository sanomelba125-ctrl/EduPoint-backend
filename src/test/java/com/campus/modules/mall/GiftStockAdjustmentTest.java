package com.campus.modules.mall;

import com.campus.common.exception.BusinessException;
import com.campus.modules.mall.dto.GiftVO;
import com.campus.modules.mall.entity.Gift;
import com.campus.modules.mall.mapper.GiftMapper;
import com.campus.modules.mall.mapper.StudentGiftMapper;
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
}
