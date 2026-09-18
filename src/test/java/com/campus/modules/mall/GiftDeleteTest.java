package com.campus.modules.mall;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.campus.modules.mall.entity.Gift;
import com.campus.modules.mall.mapper.GiftMapper;
import com.campus.modules.mall.mapper.StudentGiftMapper;
import com.campus.modules.mall.service.impl.GiftServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 礼品删除与自动降级下架单元测试
 */
class GiftDeleteTest {

    private GiftMapper giftMapper;
    private StudentGiftMapper studentGiftMapper;
    private GiftServiceImpl giftService;

    @BeforeEach
    void setUp() {
        giftMapper = mock(GiftMapper.class);
        studentGiftMapper = mock(StudentGiftMapper.class);
        giftService = new GiftServiceImpl(studentGiftMapper);
        // 注入 MyBatis-Plus ServiceImpl 的 baseMapper
        ReflectionTestUtils.setField(giftService, "baseMapper", giftMapper);
    }

    @Test
    @DisplayName("测试礼品不存在时，返回友好提示")
    void testDeleteGift_NotExists() {
        when(giftMapper.selectById(any(Serializable.class))).thenReturn(null);

        String result = giftService.deleteGift(999L);
        assertEquals("礼品不存在", result);
        verify(studentGiftMapper, never()).selectCount(any());
        verify(giftMapper, never()).deleteById(any(Serializable.class));
    }

    @Test
    @DisplayName("测试有兑换记录的礼品：不执行物理删除，自动降级为下架并正常返回，不抛异常回滚")
    void testDeleteGift_WithExchangeRecords_ShouldDowngradeToOffShelf() {
        Gift gift = new Gift();
        gift.setId(1L);
        gift.setName("定制环保袋");
        gift.setStatus(1); // 上架状态
        gift.setVersion(0);

        when(giftMapper.selectById(1L)).thenReturn(gift);
        when(studentGiftMapper.selectCount(any())).thenReturn(3L); // 存在 3 笔兑换记录
        when(giftMapper.updateById(any(Gift.class))).thenReturn(1);

        String result = giftService.deleteGift(1L);

        // 1. 验证返回提示文案明确提示已转为下架
        assertNotNull(result);
        assertTrue(result.contains("已自动转为下架状态"));

        // 2. 验证严禁物理删除
        verify(giftMapper, never()).deleteById(any(Serializable.class));

        // 3. 验证礼品状态已设为 0 (下架)
        ArgumentCaptor<Gift> captor = ArgumentCaptor.forClass(Gift.class);
        verify(giftMapper, times(1)).updateById(captor.capture());
        assertEquals(0, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("测试无兑换记录的礼品：执行物理删除")
    void testDeleteGift_WithoutExchangeRecords_ShouldPhysicallyDelete() {
        Gift gift = new Gift();
        gift.setId(2L);
        gift.setName("全新测试纪念徽章");
        gift.setStatus(1);
        gift.setVersion(0);

        when(giftMapper.selectById(2L)).thenReturn(gift);
        when(studentGiftMapper.selectCount(any())).thenReturn(0L); // 无兑换记录
        when(giftMapper.deleteById(2L)).thenReturn(1);

        String result = giftService.deleteGift(2L);

        // 1. 验证返回提示删除成功
        assertEquals("删除成功", result);

        // 2. 验证执行了物理删除
        verify(giftMapper, times(1)).deleteById(2L);
        verify(giftMapper, never()).updateById(any(Gift.class));
    }
}
