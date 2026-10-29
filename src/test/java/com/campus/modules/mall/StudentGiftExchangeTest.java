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
import org.mockito.InOrder;
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
    private com.campus.modules.mall.service.GiftIdempotentService giftIdempotentService;
    private com.campus.modules.mall.service.GiftStockRedisService giftStockRedisService;
    private com.campus.modules.mall.service.LocalMessageService localMessageService;
    private com.campus.modules.mall.service.GiftCacheService giftCacheService;

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
        giftIdempotentService = mock(com.campus.modules.mall.service.GiftIdempotentService.class);
        giftStockRedisService = mock(com.campus.modules.mall.service.GiftStockRedisService.class);
        localMessageService = mock(com.campus.modules.mall.service.LocalMessageService.class);
        giftCacheService = mock(com.campus.modules.mall.service.GiftCacheService.class);

        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(giftIdempotentService.tryAcquireSlot(anyLong(), anyString())).thenReturn(true);
        when(giftIdempotentService.verifyAndConsumeToken(anyLong(), anyLong(), anyString())).thenReturn(true);

        studentGiftService = new StudentGiftServiceImpl(
                giftMapper,
                studentMapper,
                pointLedger,
                sysNoticeService,
                rabbitTemplate,
                redis,
                null,
                null,
                giftIdempotentService,
                null,
                giftStockRedisService,
                localMessageService,
                giftCacheService
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
        verify(giftMapper, never()).deductStockIfEnough(anyLong(), anyInt());
        verify(studentMapper, never()).deductCurrentPoints(anyLong(), anyInt());
        verify(studentGiftMapper, never()).insert(any(StudentGift.class));
        verifyNoInteractions(pointLedger);
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
        verify(giftMapper, never()).deductStockIfEnough(anyLong(), anyInt());
        verify(studentMapper, never()).deductCurrentPoints(anyLong(), anyInt());
        verify(studentGiftMapper, never()).insert(any(StudentGift.class));
    }

    @Test
    @DisplayName("测试并发抢兑库存被抢完：条件扣减返回 0 行时按库存不足拦截，且不扣积分、不落单")
    void testExchangeGift_StockTakenByOthers_ShouldThrowStockEmpty() {
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

        // 模拟并发抢占：读取时还有 5 件，落库扣减时已被别人买走，条件更新影响 0 行
        when(giftMapper.deductStockIfEnough(eq(giftId), eq(1))).thenReturn(0);

        GiftExchangeDTO dto = new GiftExchangeDTO();
        dto.setGiftId(giftId);
        dto.setQuantity(1);

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            studentGiftService.exchangeGift(dto, studentId);
        });

        assertTrue(ex.getMessage().contains("抢光") || ex.getMessage().contains("不足"),
                "异常信息应提示库存不足，实际为: " + ex.getMessage());

        // 扣库存失败后，不能继续扣除学生积分
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
        when(giftMapper.deductStockIfEnough(eq(giftId), eq(1))).thenReturn(1);

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
        verify(giftMapper, times(1)).deductStockIfEnough(eq(giftId), eq(1));
        verify(studentMapper, times(1)).deductCurrentPoints(eq(studentId), eq(80));
        verify(studentGiftMapper, times(1)).insert(any(StudentGift.class));
        verify(pointLedger, times(1)).record(
                eq(studentId), eq(2), eq(-80), eq(20), anyString(), anyString(), eq(8888L), isNull()
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
        // P0-1：MySQL 加回去只是第一步，秒杀入口读的是 Redis，必须同步回补，
        // 否则这一件在秒杀侧永远卖不出去（实测线上 gift1 因此少了 3 件）
        verify(giftStockRedisService, times(1)).compensateStockOnce(eq(giftId), eq(1), eq("cancel:" + orderId));
        verify(giftStockRedisService, times(1)).evictGiftCacheWithDoubleDelete(eq(giftId));
        // 验证积分原路返还 +80
        verify(studentMapper, times(1)).addCurrentPoints(eq(studentId), eq(80));
        // 验证记录退分流水台账
        verify(pointLedger, times(1)).record(
                eq(studentId), eq(4), eq(80), eq(100), anyString(), eq("ORD202609180005"), eq(orderId), isNull()
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
        // P0-1：管理员驳回同样要回补 Redis，回补件数取订单数量而不是 1
        verify(giftStockRedisService, times(1)).compensateStockOnce(eq(giftId), eq(2), eq("cancel:" + orderId));
        verify(giftStockRedisService, times(1)).evictGiftCacheWithDoubleDelete(eq(giftId));
        // 验证积分原路返还 +160
        verify(studentMapper, times(1)).addCurrentPoints(eq(studentId), eq(160));
        // 验证记录退分流水台账
        verify(pointLedger, times(1)).record(
                eq(studentId), eq(4), eq(160), eq(210), anyString(), eq("ORD202609180006"), eq(orderId), eq(adminId)
        );
    }

    @Test
    @DisplayName("P0-1 反向：MySQL 库存回补未生效（受影响行数 0）时不得回补 Redis，避免凭空造出库存")
    void testCancelOrder_IncreaseStockFailed_ShouldNotCompensateRedis() {
        Long studentId = 108L;
        Long orderId = 503L;
        Long giftId = 7L;

        StudentGift order = new StudentGift();
        order.setId(orderId);
        order.setOrderNo("ORD202610290003");
        order.setStudentId(studentId);
        order.setGiftId(giftId);
        order.setQuantity(1);
        order.setPointsCost(50);
        order.setStatus(1);

        when(studentGiftMapper.selectById(orderId)).thenReturn(order);
        when(studentGiftMapper.cancelOrderWithStatusGuard(eq(orderId), eq(1), eq(1), any(LocalDateTime.class), anyString(), isNull()))
                .thenReturn(1);
        // 礼品已被删除等场景：MySQL 根本没加回去
        when(giftMapper.increaseStock(eq(giftId), eq(1))).thenReturn(0);

        assertDoesNotThrow(() -> studentGiftService.studentCancelOrder(orderId, studentId));

        // MySQL 没加、Redis 就更不能加，否则就是超卖
        verify(giftStockRedisService, never()).compensateStockOnce(anyLong(), anyInt(), anyString());
        verify(giftStockRedisService, never()).evictGiftCacheWithDoubleDelete(anyLong());
    }

    @Test
    @DisplayName("P0-1：24h 超时关单同样要把库存回补到 Redis，并按订单数量回补")
    void testExpireOrder_ShouldCompensateRedisStock() {
        Long studentId = 109L;
        Long orderId = 504L;
        Long giftId = 8L;

        StudentGift order = new StudentGift();
        order.setId(orderId);
        order.setOrderNo("ORD202610290004");
        order.setStudentId(studentId);
        order.setGiftId(giftId);
        order.setQuantity(3);
        order.setPointsCost(150);
        order.setStatus(1);

        when(studentGiftMapper.selectById(orderId)).thenReturn(order);
        // 超时关单传 operatorId = 0L
        when(studentGiftMapper.cancelOrderWithStatusGuard(eq(orderId), eq(1), eq(1), any(LocalDateTime.class), anyString(), eq(0L)))
                .thenReturn(1);
        when(giftMapper.increaseStock(eq(giftId), eq(3))).thenReturn(1);

        Student student = new Student();
        student.setId(studentId);
        student.setCurrentPoints(0);
        student.setDeleted(0);
        when(studentMapper.selectById(studentId)).thenReturn(student);
        when(studentMapper.selectCurrentPoints(eq(studentId))).thenReturn(150);

        assertDoesNotThrow(() -> studentGiftService.expireOrder(orderId));

        verify(giftMapper, times(1)).increaseStock(eq(giftId), eq(3));
        verify(giftStockRedisService, times(1)).compensateStockOnce(eq(giftId), eq(3), eq("cancel:" + orderId));
    }

    @Test
    @DisplayName("P0-1 容错：Redis 回补抛异常时不能影响已经生效的取消事务")
    void testCancelOrder_RedisCompensationFails_ShouldNotBreakCancel() {
        Long studentId = 110L;
        Long orderId = 505L;
        Long giftId = 9L;

        StudentGift order = new StudentGift();
        order.setId(orderId);
        order.setOrderNo("ORD202610290005");
        order.setStudentId(studentId);
        order.setGiftId(giftId);
        order.setQuantity(1);
        order.setPointsCost(30);
        order.setStatus(1);

        when(studentGiftMapper.selectById(orderId)).thenReturn(order);
        when(studentGiftMapper.cancelOrderWithStatusGuard(eq(orderId), eq(1), eq(1), any(LocalDateTime.class), anyString(), isNull()))
                .thenReturn(1);
        when(giftMapper.increaseStock(eq(giftId), eq(1))).thenReturn(1);
        when(giftStockRedisService.compensateStockOnce(eq(giftId), eq(1), eq("cancel:" + orderId)))
                .thenThrow(new IllegalStateException("redis down"));

        // 取消事务已经提交，外部副作用失败只能记录日志，绝不能让接口报错或回滚
        assertDoesNotThrow(() -> studentGiftService.studentCancelOrder(orderId, studentId));
        verify(giftMapper, times(1)).increaseStock(eq(giftId), eq(1));
    }

    @Test
    @DisplayName("P1-6：秒杀入口占位成功后必须注册回滚释放，否则同一 requestId 被锁满 TTL（30s）无法重试")
    void testSeckillExchange_ShouldRegisterSlotReleaseOnRollback() {
        Long studentId = 111L;
        Long giftId = 3L;
        String requestId = "SEC-P16-REQ";

        when(giftIdempotentService.tryAcquireSlot(eq(studentId), eq(requestId))).thenReturn(true);
        // 令牌已过期/已被使用：属于客户端可立即重试的场景
        when(giftIdempotentService.verifyAndConsumeToken(eq(studentId), eq(giftId), anyString())).thenReturn(false);

        GiftExchangeDTO dto = new GiftExchangeDTO();
        dto.setGiftId(giftId);
        dto.setQuantity(1);
        dto.setRequestId(requestId);
        dto.setExchangeToken("EXPIRED-TOKEN");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> studentGiftService.seckillExchange(dto, studentId));
        assertTrue(ex.getMessage().contains("兑换令牌已失效"), "异常信息应为令牌失效，实际为: " + ex.getMessage());

        // 释放必须在「拿到占位之后、业务失败之前」注册，否则事务回滚时没人清 key
        InOrder inOrder = inOrder(giftIdempotentService);
        inOrder.verify(giftIdempotentService).tryAcquireSlot(eq(studentId), eq(requestId));
        inOrder.verify(giftIdempotentService).releaseSlotOnRollback(eq(studentId), eq(requestId));
        inOrder.verify(giftIdempotentService).verifyAndConsumeToken(eq(studentId), eq(giftId), anyString());
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

    @Test
    @DisplayName("测试获取兑换防重令牌：验证学籍与礼品状态正常时成功签发 Token")
    void testGetExchangeToken_Success() {
        Long studentId = 108L;
        Long giftId = 8L;

        Student student = new Student();
        student.setId(studentId);
        student.setStatus(1);
        when(studentMapper.selectById(studentId)).thenReturn(student);

        Gift gift = new Gift();
        gift.setId(giftId);
        gift.setStatus(1);
        gift.setDeleted(0);
        when(giftMapper.selectById(giftId)).thenReturn(gift);

        com.campus.modules.mall.dto.GiftExchangeTokenVO expectedVo = com.campus.modules.mall.dto.GiftExchangeTokenVO.builder()
                .token("EXT-testtoken123")
                .giftId(giftId)
                .expireSeconds(600L)
                .build();
        when(giftIdempotentService.generateExchangeToken(studentId, giftId)).thenReturn(expectedVo);

        com.campus.modules.mall.dto.GiftExchangeTokenVO result = studentGiftService.getExchangeToken(giftId, studentId);

        assertNotNull(result);
        assertEquals("EXT-testtoken123", result.getToken());
        assertEquals(giftId, result.getGiftId());
        verify(giftIdempotentService, times(1)).generateExchangeToken(studentId, giftId);
    }

    @Test
    @DisplayName("测试被冻结学生获取防重令牌被拦截")
    void testGetExchangeToken_FrozenStudent_ShouldThrowException() {
        Long studentId = 109L;
        Long giftId = 8L;

        Student student = new Student();
        student.setId(studentId);
        student.setStatus(0); // 冻结
        when(studentMapper.selectById(studentId)).thenReturn(student);

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            studentGiftService.getExchangeToken(giftId, studentId);
        });

        assertTrue(ex.getMessage().contains("冻结"));
        verify(giftIdempotentService, never()).generateExchangeToken(anyLong(), anyLong());
    }

    @Test
    @DisplayName("测试兑换时防重 Token 失效拦截：验证抛出“兑换令牌已失效”异常")
    void testExchangeGift_InvalidToken_ShouldThrowException() {
        Long studentId = 110L;
        Long giftId = 9L;

        when(giftIdempotentService.verifyAndConsumeToken(eq(studentId), eq(giftId), eq("EXT-expired"))).thenReturn(false);

        GiftExchangeDTO dto = new GiftExchangeDTO();
        dto.setGiftId(giftId);
        dto.setQuantity(1);
        dto.setExchangeToken("EXT-expired");

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            studentGiftService.exchangeGift(dto, studentId);
        });

        assertTrue(ex.getMessage().contains("兑换令牌已失效或已被使用"));
        verify(giftMapper, never()).deductStockIfEnough(anyLong(), anyInt());
        verify(studentMapper, never()).deductCurrentPoints(anyLong(), anyInt());
    }

    @Test
    @DisplayName("测试命中既有订单快路径：弱网重发直接返回历史订单，不重复扣分与扣库")
    void testExchangeGift_ExistingOrder_FastPathReturnsDirectly() {
        Long studentId = 111L;
        Long giftId = 10L;
        String requestId = "REQ-EXISTING-001";

        StudentGift existingOrder = new StudentGift();
        existingOrder.setId(666L);
        existingOrder.setOrderNo("ORD20260923000666");
        existingOrder.setStudentId(studentId);
        existingOrder.setGiftId(giftId);
        existingOrder.setQuantity(1);
        existingOrder.setPointsCost(100);
        existingOrder.setPickupCode("PK999988");
        existingOrder.setRequestId(requestId);

        when(giftIdempotentService.findExistingOrder(studentId, requestId)).thenReturn(existingOrder);
        when(studentMapper.selectCurrentPoints(studentId)).thenReturn(50);

        Gift gift = new Gift();
        gift.setId(giftId);
        gift.setName("既有礼品");
        when(giftMapper.selectById(giftId)).thenReturn(gift);

        GiftExchangeDTO dto = new GiftExchangeDTO();
        dto.setGiftId(giftId);
        dto.setRequestId(requestId);

        GiftExchangeVO vo = studentGiftService.exchangeGift(dto, studentId);

        assertNotNull(vo);
        assertEquals("ORD20260923000666", vo.getOrderNo());
        assertEquals("PK999988", vo.getPickupCode());
        assertEquals(100, vo.getPointsCost());
        // 验证不会走扣减与落库
        verify(giftMapper, never()).deductStockIfEnough(anyLong(), anyInt());
        verify(studentMapper, never()).deductCurrentPoints(anyLong(), anyInt());
        verify(studentGiftMapper, never()).insert(any(StudentGift.class));
    }

    @Test
    @DisplayName("测试并发重复点击拦截：SETNX 槽位申请失败抛出“正在处理中”")
    void testExchangeGift_ConcurrentConflict_ShouldThrowException() {
        Long studentId = 112L;
        Long giftId = 11L;
        String requestId = "REQ-CONCURRENT-001";

        when(giftIdempotentService.tryAcquireSlot(studentId, requestId)).thenReturn(false);

        GiftExchangeDTO dto = new GiftExchangeDTO();
        dto.setGiftId(giftId);
        dto.setRequestId(requestId);

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            studentGiftService.exchangeGift(dto, studentId);
        });

        assertTrue(ex.getMessage().contains("兑换请求正在处理中，请勿重复提交"));
        verify(giftMapper, never()).deductStockIfEnough(anyLong(), anyInt());
    }
}
