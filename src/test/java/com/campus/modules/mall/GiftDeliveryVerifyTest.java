package com.campus.modules.mall;

import com.campus.common.exception.BusinessException;
import com.campus.modules.system.service.SysNoticeService;
import com.campus.modules.mall.dto.DeliveryConfirmDTO;
import com.campus.modules.mall.entity.StudentGift;
import com.campus.modules.mall.mapper.GiftMapper;
import com.campus.modules.mall.mapper.StudentGiftMapper;
import com.campus.modules.mall.service.PointLedger;
import com.campus.modules.mall.service.impl.StudentGiftServiceImpl;
import com.campus.modules.student.mapper.StudentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 线下提货码核销比对单测 (GiftDeliveryVerifyTest)
 */
class GiftDeliveryVerifyTest {

    private StudentGiftMapper studentGiftMapper;
    private GiftMapper giftMapper;
    private StudentMapper studentMapper;
    private PointLedger pointLedger;
    private SysNoticeService sysNoticeService;
    private RabbitTemplate rabbitTemplate;
    private StringRedisTemplate redis;

    private StudentGiftServiceImpl studentGiftService;

    @BeforeEach
    void setUp() {
        studentGiftMapper = mock(StudentGiftMapper.class);
        giftMapper = mock(GiftMapper.class);
        studentMapper = mock(StudentMapper.class);
        pointLedger = mock(PointLedger.class);
        sysNoticeService = mock(SysNoticeService.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        redis = mock(StringRedisTemplate.class);

        studentGiftService = new StudentGiftServiceImpl(
                giftMapper,
                studentMapper,
                pointLedger,
                sysNoticeService,
                rabbitTemplate,
                redis
        );
        ReflectionTestUtils.setField(studentGiftService, "baseMapper", studentGiftMapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "PK 1234 5678",      // 包含空格
            "pk-1234-5678",      // 包含横杠且小写
            "pk12345678",        // 纯小写紧凑格式
            "  PK-1234-5678  ",  // 首尾包含空格
            "PK12345678"         // 标准无分隔符大写
    })
    @DisplayName("测试提货码容错匹配：支持忽略空格、横杠、大小写，均能正确识别并成功核销")
    void testConfirmDelivery_ToleranceMatching_Success(String userInputCode) {
        Long orderId = 1001L;
        Long adminId = 1L;

        StudentGift order = new StudentGift();
        order.setId(orderId);
        order.setOrderNo("ORD202609180001");
        order.setStudentId(2001L);
        order.setPickupCode("PK12345678"); // 库中存储的标准格式
        order.setStatus(1); // 1-待提货

        when(studentGiftMapper.selectById(orderId)).thenReturn(order);
        when(studentGiftMapper.confirmDeliveryWithStatusGuard(eq(orderId), eq(1), any(LocalDateTime.class), eq(adminId), any()))
                .thenReturn(1);

        DeliveryConfirmDTO dto = new DeliveryConfirmDTO();
        dto.setOrderId(orderId);
        dto.setPickupCode(userInputCode);
        dto.setRemark("前台正常核验提货");

        assertDoesNotThrow(() -> studentGiftService.confirmDelivery(dto, adminId));

        // 验证调用了底层数据库带状态守卫的核销更新
        verify(studentGiftMapper, times(1)).confirmDeliveryWithStatusGuard(
                eq(orderId), eq(1), any(LocalDateTime.class), eq(adminId), eq("前台正常核验提货")
        );
    }

    @Test
    @DisplayName("测试提货码错误拦截：提货码不匹配时抛出异常并包含“核销失败”")
    void testConfirmDelivery_MismatchedCode_ShouldThrowException() {
        Long orderId = 1002L;
        Long adminId = 1L;

        StudentGift order = new StudentGift();
        order.setId(orderId);
        order.setOrderNo("ORD202609180002");
        order.setStudentId(2002L);
        order.setPickupCode("PK12345678");
        order.setStatus(1);

        when(studentGiftMapper.selectById(orderId)).thenReturn(order);

        DeliveryConfirmDTO dto = new DeliveryConfirmDTO();
        dto.setOrderId(orderId);
        dto.setPickupCode("PK99998888"); // 错误的提货码

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            studentGiftService.confirmDelivery(dto, adminId);
        });

        assertTrue(ex.getMessage().contains("核销失败"), "提货码不匹配时应提示核销失败，实际为: " + ex.getMessage());

        // 确认不会执行数据库状态流转更新
        verify(studentGiftMapper, never()).confirmDeliveryWithStatusGuard(anyLong(), anyInt(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("测试防止二次核销：已核销订单（status=2）再次调用核销，抛出“该订单已核销，不可重复提货”")
    void testConfirmDelivery_AlreadyCompleted_ShouldThrowException() {
        Long orderId = 1003L;
        Long adminId = 1L;

        StudentGift order = new StudentGift();
        order.setId(orderId);
        order.setOrderNo("ORD202609180003");
        order.setStudentId(2003L);
        order.setPickupCode("PK12345678");
        order.setStatus(2); // 2-已核销/已提货

        when(studentGiftMapper.selectById(orderId)).thenReturn(order);

        DeliveryConfirmDTO dto = new DeliveryConfirmDTO();
        dto.setOrderId(orderId);
        dto.setPickupCode("PK12345678");

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            studentGiftService.confirmDelivery(dto, adminId);
        });

        assertTrue(ex.getMessage().contains("该订单已核销，不可重复提货"), "已核销订单再次调用应拦截，实际为: " + ex.getMessage());
        verify(studentGiftMapper, never()).confirmDeliveryWithStatusGuard(anyLong(), anyInt(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("测试并发核销状态守卫拦截：并发冲突导致影响行数为 0 时，拦截并抛出不可重复提货异常")
    void testConfirmDelivery_ConcurrentConflict_StatusGuardFails() {
        Long orderId = 1004L;
        Long adminId = 1L;

        StudentGift order = new StudentGift();
        order.setId(orderId);
        order.setOrderNo("ORD202609180004");
        order.setStudentId(2004L);
        order.setPickupCode("PK12345678");
        order.setStatus(1); // 查出来是 1，但被其他管理员并发核销

        when(studentGiftMapper.selectById(orderId)).thenReturn(order);
        // 模拟状态守卫 CAS 失败返回 0
        when(studentGiftMapper.confirmDeliveryWithStatusGuard(eq(orderId), eq(1), any(LocalDateTime.class), eq(adminId), any()))
                .thenReturn(0);

        DeliveryConfirmDTO dto = new DeliveryConfirmDTO();
        dto.setOrderId(orderId);
        dto.setPickupCode("PK12345678");

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            studentGiftService.confirmDelivery(dto, adminId);
        });

        assertTrue(ex.getMessage().contains("不可重复"), "并发核销未生效时应阻断并提示不可重复，实际为: " + ex.getMessage());
    }

    @Test
    @DisplayName("测试订单不存在时核销拦截")
    void testConfirmDelivery_OrderNotFound_ShouldThrowException() {
        Long orderId = 9999L;
        when(studentGiftMapper.selectById(orderId)).thenReturn(null);

        DeliveryConfirmDTO dto = new DeliveryConfirmDTO();
        dto.setOrderId(orderId);
        dto.setPickupCode("PK12345678");

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            studentGiftService.confirmDelivery(dto, 1L);
        });

        assertTrue(ex.getMessage().contains("不存在"));
    }
}
