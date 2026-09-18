package com.campus.modules.mall;

import com.campus.common.exception.BusinessException;
import com.campus.modules.system.service.SysNoticeService;
import com.campus.modules.mall.dto.GiftExchangeDTO;
import com.campus.modules.mall.dto.GiftExchangeVO;
import com.campus.modules.mall.dto.OrderCancelDTO;
import com.campus.modules.mall.entity.Gift;
import com.campus.modules.mall.entity.StudentGift;
import com.campus.modules.mall.mapper.GiftMapper;
import com.campus.modules.mall.mapper.StudentGiftMapper;
import com.campus.modules.mall.service.PointLedger;
import com.campus.modules.mall.service.impl.StudentGiftServiceImpl;
import com.campus.modules.student.entity.Student;
import com.campus.modules.student.mapper.StudentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 礼品兑换下单全链路单测 (StudentGiftExchangeTest)
 */
class StudentGiftExchangeTest {

    private StudentGiftMapper studentGiftMapper;
    private GiftMapper giftMapper;
    private StudentMapper studentMapper;
    private PointLedger pointLedger;
    private SysNoticeService sysNoticeService;
    private RabbitTemplate rabbitTemplate;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOperations;

    private StudentGiftServiceImpl studentGiftService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        studentGiftMapper = mock(StudentGiftMapper.class);
        giftMapper = mock(GiftMapper.class);
        studentMapper = mock(StudentMapper.class);
        pointLedger = mock(PointLedger.class);
        sysNoticeService = mock(SysNoticeService.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        redis = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);

        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

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

    @Test
    @DisplayName("测试积分余额不足拦截：学生积分 50，礼品需要 80，验证抛出“可用积分不足”并阻断后续扣减")
    void testExchangeGift_InsufficientPoints_ShouldThrowException() {
        Long studentId = 101L;
        Long giftId = 1L;

        // 学生积分 50
        Student student = new Student();
        student.setId(studentId);
        student.setCurrentPoints(50);
        student.setStatus(1);
        when(studentMapper.selectById(studentId)).thenReturn(student);

        // 礼品需要 80 积分，库存充足
        Gift gift = new Gift();
        gift.setId(giftId);
        gift.setName("定制双肩包");
        gift.setPointsRequired(80);
        gift.setStock(10);
        gift.setStatus(1);
        gift.setDeleted(0);
        when(giftMapper.selectById(giftId)).thenReturn(gift);

        GiftExchangeDTO dto = new GiftExchangeDTO();
        dto.setGiftId(giftId);
        dto.setQuantity(1);

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            studentGiftService.exchangeGift(dto, studentId);
        });

        assertTrue(ex.getMessage().contains("可用积分不足"), "异常信息应包含“可用积分不足”，实际为: " + ex.getMessage());

        // 确保不会触发库存扣减与积分扣减动作
        verify(giftMapper, never()).reduceStockWithOptimisticLock(anyLong(), anyInt(), anyInt());
        verify(studentMapper, never()).deductCurrentPoints(anyLong(), anyInt());
        verify(studentGiftMapper, never()).insert(any(StudentGift.class));
        verify(pointLedger, never()).record(anyLong(), anyInt(), anyInt(), anyInt(), anyString(), any(), any());
    }

    @Test
    @DisplayName("测试并发超卖与无库存拦截：礼品库存为 0，验证抛出“库存已被抢光”并阻断")
    void testExchangeGift_ZeroStock_ShouldThrowException() {
        Long studentId = 102L;
        Long giftId = 2L;

        // 学生积分充足 (200)
        Student student = new Student();
        student.setId(studentId);
        student.setCurrentPoints(200);
        student.setStatus(1);
        when(studentMapper.selectById(studentId)).thenReturn(student);

        // 礼品库存为 0
        Gift gift = new Gift();
        gift.setId(giftId);
        gift.setName("限量版手办");
        gift.setPointsRequired(80);
        gift.setStock(0);
        gift.setStatus(1);
        gift.setDeleted(0);
        when(giftMapper.selectById(giftId)).thenReturn(gift);

        GiftExchangeDTO dto = new GiftExchangeDTO();
        dto.setGiftId(giftId);
        dto.setQuantity(1);

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            studentGiftService.exchangeGift(dto, studentId);
        });

        assertTrue(ex.getMessage().contains("库存已被抢光"), "异常信息应包含“库存已被抢光”，实际为: " + ex.getMessage());

        // 确保未执行库存扣减与积分扣减
        verify(giftMapper, never()).reduceStockWithOptimisticLock(anyLong(), anyInt(), anyInt());
        verify(studentMapper, never()).deductCurrentPoints(anyLong(), anyInt());
        verify(studentGiftMapper, never()).insert(any(StudentGift.class));
    }

    @Test
    @DisplayName("测试并发抢兑乐观锁冲突拦截：版本号冲突导致扣库存失败，验证抛出冲突重试提示并阻断")
    void testExchangeGift_OptimisticLockConflict_ShouldThrowException() {
        Long studentId = 103L;
        Long giftId = 3L;

        Student student = new Student();
        student.setId(studentId);
        student.setCurrentPoints(100);
        student.setStatus(1);
        when(studentMapper.selectById(studentId)).thenReturn(student);

        Gift gift = new Gift();
        gift.setId(giftId);
        gift.setName("蓝牙耳机");
        gift.setPointsRequired(80);
        gift.setStock(5);
        gift.setVersion(3);
        gift.setStatus(1);
        gift.setDeleted(0);
        when(giftMapper.selectById(giftId)).thenReturn(gift);

        // 模拟并发更新导致乐观锁 version 校验失败，返回 0 行
        when(giftMapper.reduceStockWithOptimisticLock(eq(giftId), eq(1), eq(3))).thenReturn(0);

        GiftExchangeDTO dto = new GiftExchangeDTO();
        dto.setGiftId(giftId);
        dto.setQuantity(1);

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            studentGiftService.exchangeGift(dto, studentId);
        });

        assertTrue(ex.getMessage().contains("冲突") || ex.getMessage().contains("重试"),
                "异常信息应包含冲突提示，实际为: " + ex.getMessage());

        // 乐观锁失败后，不能继续扣除学生积分
        verify(studentMapper, never()).deductCurrentPoints(anyLong(), anyInt());
        verify(studentGiftMapper, never()).insert(any(StudentGift.class));
    }

    @Test
    @DisplayName("测试正常兑换成功原子性：积分扣减 80，库存扣减 1，生成唯一提货码（前缀 PK）与订单号")
    void testExchangeGift_Success_Atomicity() {
        Long studentId = 104L;
        Long giftId = 4L;

        Student student = new Student();
        student.setId(studentId);
        student.setName("李华");
        student.setPhone("13800001111");
        student.setCurrentPoints(100);
        student.setStatus(1);
        when(studentMapper.selectById(studentId)).thenReturn(student);

        Gift gift = new Gift();
        gift.setId(giftId);
        gift.setName("智能保温杯");
        gift.setPointsRequired(80);
        gift.setStock(10);
        gift.setVersion(1);
        gift.setStatus(1);
        gift.setDeleted(0);
        when(giftMapper.selectById(giftId)).thenReturn(gift);

        // 1. 模拟乐观锁扣库存成功
        when(giftMapper.reduceStockWithOptimisticLock(eq(giftId), eq(1), eq(1))).thenReturn(1);

        // 2. 模拟原子扣积分成功，扣减后剩余 20 积分
        when(studentMapper.deductCurrentPoints(eq(studentId), eq(80))).thenReturn(1);
        when(studentMapper.selectCurrentPoints(eq(studentId))).thenReturn(20);

        // 3. 提货码查重放行
        when(studentGiftMapper.selectCount(any())).thenReturn(0L);

        // 4. 模拟订单落库，赋予自增 ID 8888L
        doAnswer(invocation -> {
            StudentGift entity = invocation.getArgument(0);
            entity.setId(8888L);
            return 1;
        }).when(studentGiftMapper).insert(any(StudentGift.class));

        GiftExchangeDTO dto = new GiftExchangeDTO();
        dto.setGiftId(giftId);
        dto.setQuantity(1);
        dto.setReceiverName("李华");
        dto.setReceiverPhone("13800001111");

        GiftExchangeVO result = studentGiftService.exchangeGift(dto, studentId);

        // 验证返回结果
        assertNotNull(result);
        assertEquals(80, result.getPointsCost(), "消耗积分应为 80");
        assertEquals(20, result.getRemainPoints(), "剩余积分应为 20");
        assertEquals("智能保温杯", result.getGiftName());
        assertEquals(1, result.getQuantity());
        assertNotNull(result.getOrderNo(), "必须生成订单号");
        assertTrue(result.getOrderNo().startsWith("ORD"), "订单号必须以 ORD 开头");
        assertNotNull(result.getPickupCode(), "必须生成提货码");
        assertTrue(result.getPickupCode().startsWith("PK"), "提货码必须以 PK 开头");

        // 验证关键原子操作均被正确调用
        verify(giftMapper, times(1)).reduceStockWithOptimisticLock(eq(giftId), eq(1), eq(1));
        verify(studentMapper, times(1)).deductCurrentPoints(eq(studentId), eq(80));
        verify(studentGiftMapper, times(1)).insert(any(StudentGift.class));
        verify(pointLedger, times(1)).record(
                eq(studentId), eq(2), eq(-80), eq(20), anyString(), eq(8888L), isNull()
        );
    }

    @Test
    @DisplayName("测试学生自主取消订单：验证积分原路全额退回 80，礼品库存原子恢复 +1")
    void testStudentCancelOrder_Success_RefundPointsAndRestock() {
        Long studentId = 105L;
        Long orderId = 501L;
        Long giftId = 5L;

        StudentGift order = new StudentGift();
        order.setId(orderId);
        order.setOrderNo("ORD202609180005");
        order.setStudentId(studentId);
        order.setGiftId(giftId);
        order.setQuantity(1);
        order.setPointsCost(80);
        order.setStatus(1); // 1-待提货

        when(studentGiftMapper.selectById(orderId)).thenReturn(order);
        // 模拟状态守卫取消成功
        when(studentGiftMapper.cancelOrderWithStatusGuard(eq(orderId), eq(1), eq(1), any(LocalDateTime.class), anyString(), isNull()))
                .thenReturn(1);
        // 模拟库存恢复 +1 成功
        when(giftMapper.increaseStock(eq(giftId), eq(1))).thenReturn(1);

        Student student = new Student();
        student.setId(studentId);
        student.setCurrentPoints(20);
        student.setDeleted(0);
        when(studentMapper.selectById(studentId)).thenReturn(student);
        when(studentMapper.addCurrentPoints(eq(studentId), eq(80))).thenReturn(1);
        when(studentMapper.selectCurrentPoints(eq(studentId))).thenReturn(100);

        assertDoesNotThrow(() -> studentGiftService.studentCancelOrder(orderId, studentId));

        // 验证礼品库存原子恢复 +1
        verify(giftMapper, times(1)).increaseStock(eq(giftId), eq(1));
        // 验证积分原路返还 +80
        verify(studentMapper, times(1)).addCurrentPoints(eq(studentId), eq(80));
        // 验证记录退分流水台账
        verify(pointLedger, times(1)).record(
                eq(studentId), eq(4), eq(80), eq(100), anyString(), eq(orderId), isNull()
        );
    }

    @Test
    @DisplayName("测试管理员取消订单：验证积分原路全额退回 160，礼品库存恢复 +2")
    void testAdminCancelOrder_Success_RefundPointsAndRestock() {
        Long studentId = 106L;
        Long orderId = 502L;
        Long giftId = 6L;
        Long adminId = 1L;

        StudentGift order = new StudentGift();
        order.setId(orderId);
        order.setOrderNo("ORD202609180006");
        order.setStudentId(studentId);
        order.setGiftId(giftId);
        order.setQuantity(2);
        order.setPointsCost(160);
        order.setStatus(1); // 1-待提货

        when(studentGiftMapper.selectById(orderId)).thenReturn(order);
        when(studentGiftMapper.cancelOrderWithStatusGuard(eq(orderId), eq(1), eq(1), any(LocalDateTime.class), anyString(), eq(adminId)))
                .thenReturn(1);
        when(giftMapper.increaseStock(eq(giftId), eq(2))).thenReturn(1);

        Student student = new Student();
        student.setId(studentId);
        student.setCurrentPoints(50);
        student.setDeleted(0);
        when(studentMapper.selectById(studentId)).thenReturn(student);
        when(studentMapper.addCurrentPoints(eq(studentId), eq(160))).thenReturn(1);
        when(studentMapper.selectCurrentPoints(eq(studentId))).thenReturn(210);

        OrderCancelDTO dto = new OrderCancelDTO();
        dto.setOrderId(orderId);
        dto.setCancelReason("礼品缺货，管理员主动取消退单");
        dto.setRefundPoints(true);

        assertDoesNotThrow(() -> studentGiftService.adminCancelOrder(dto, adminId));

        // 验证礼品库存原子恢复 +2
        verify(giftMapper, times(1)).increaseStock(eq(giftId), eq(2));
        // 验证积分原路返还 +160
        verify(studentMapper, times(1)).addCurrentPoints(eq(studentId), eq(160));
        // 验证记录退分流水台账
        verify(pointLedger, times(1)).record(
                eq(studentId), eq(4), eq(160), eq(210), anyString(), eq(orderId), eq(adminId)
        );
    }

    @Test
    @DisplayName("测试非待提货状态订单不允许取消")
    void testCancelOrder_NonPendingStatus_ShouldThrowException() {
        Long studentId = 107L;
        Long orderId = 503L;

        StudentGift order = new StudentGift();
        order.setId(orderId);
        order.setStudentId(studentId);
        order.setStatus(2); // 已提货完成

        when(studentGiftMapper.selectById(orderId)).thenReturn(order);

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            studentGiftService.studentCancelOrder(orderId, studentId);
        });

        assertTrue(ex.getMessage().contains("仅处于【待发放/待提货】状态"));
        verify(giftMapper, never()).increaseStock(anyLong(), anyInt());
        verify(studentMapper, never()).addCurrentPoints(anyLong(), anyInt());
    }
}
