package com.campus.modules.mall;

import com.campus.modules.mall.dto.SeckillResultVO;
import com.campus.modules.mall.entity.Gift;
import com.campus.modules.mall.entity.StudentGift;
import com.campus.modules.mall.enums.OrderStatus;
import com.campus.modules.mall.mapper.GiftMapper;
import com.campus.modules.mall.mapper.StudentGiftMapper;
import com.campus.modules.mall.service.GiftCacheService;
import com.campus.modules.mall.service.GiftStockRedisService;
import com.campus.modules.mall.service.LocalMessageService;
import com.campus.modules.mall.service.PointLedger;
import com.campus.modules.mall.service.impl.StudentGiftServiceImpl;
import com.campus.modules.student.mapper.StudentMapper;
import com.campus.modules.system.service.SysNoticeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
 * 秒杀结果轮询三态单测。
 *
 * <p>核心断言：查不到订单时不能一律回「处理中」—— 有失败标记必须返回 FAILED 与原因，
 * 否则业务校验失败或消息判死的订单会让客户端永久排队。
 */
@ExtendWith(MockitoExtension.class)
public class SeckillResultQueryTest {

    @Mock
    private GiftMapper giftMapper;
    @Mock
    private StudentMapper studentMapper;
    @Mock
    private PointLedger pointLedger;
    @Mock
    private SysNoticeService sysNoticeService;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private GiftStockRedisService giftStockRedisService;
    @Mock
    private LocalMessageService localMessageService;
    @Mock
    private GiftCacheService giftCacheService;
    @Mock
    private StudentGiftMapper studentGiftMapper;

    private StudentGiftServiceImpl studentGiftService;

    private static final Long STUDENT_ID = 1001L;
    private static final String REQUEST_ID = "req-seckill-001";

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOperations);
        studentGiftService = new StudentGiftServiceImpl(
                giftMapper,
                studentMapper,
                pointLedger,
                sysNoticeService,
                rabbitTemplate,
                redis,
                null,
                null,
                null,
                null,
                giftStockRedisService,
                localMessageService,
                giftCacheService
        );
        ReflectionTestUtils.setField(studentGiftService, "baseMapper", studentGiftMapper);
    }

    @Test
    @DisplayName("订单已落库：返回 SUCCESS 并带出提货码与实时积分")
    void shouldReturnSuccessWhenOrderExists() {
        StudentGift order = new StudentGift();
        order.setId(88L);
        order.setOrderNo("ORD20261028001");
        order.setStudentId(STUDENT_ID);
        order.setGiftId(7L);
        order.setQuantity(2);
        order.setPointsCost(100);
        order.setPickupCode("PK12345678");
        order.setStatus(OrderStatus.PENDING.getCode());
        order.setCreateTime(LocalDateTime.now());

        when(studentGiftMapper.selectOne(any(), anyBoolean())).thenReturn(order);
        Gift gift = new Gift();
        gift.setId(7L);
        gift.setName("定制帆布包");
        when(giftMapper.selectById(7L)).thenReturn(gift);
        when(studentMapper.selectCurrentPoints(STUDENT_ID)).thenReturn(850);

        SeckillResultVO result = studentGiftService.getSeckillResultByRequestId(REQUEST_ID, STUDENT_ID);

        assertEquals(SeckillResultVO.STATUS_SUCCESS, result.getStatus());
        assertEquals("PK12345678", result.getPickupCode());
        assertEquals("ORD20261028001", result.getOrderNo());
        assertEquals(850, result.getRemainPoints());
        assertNull(result.getFailReason());
    }

    @Test
    @DisplayName("无订单但有失败标记：返回 FAILED 与可直接展示的原因，而不是永久排队")
    void shouldReturnFailedWhenFailureMarkerExists() {
        when(studentGiftMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(valueOperations.get("seckill:fail:" + STUDENT_ID + ":" + REQUEST_ID))
                .thenReturn("可用积分不足（需要 200 积分），本次兑换未完成");

        SeckillResultVO result = studentGiftService.getSeckillResultByRequestId(REQUEST_ID, STUDENT_ID);

        assertEquals(SeckillResultVO.STATUS_FAILED, result.getStatus());
        assertTrue(result.getFailReason().contains("积分不足"));
        assertNull(result.getPickupCode());
    }

    @Test
    @DisplayName("无订单也无失败标记：才是真正的处理中")
    void shouldReturnProcessingWhenNothingFound() {
        when(studentGiftMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(valueOperations.get(anyString())).thenReturn(null);

        SeckillResultVO result = studentGiftService.getSeckillResultByRequestId(REQUEST_ID, STUDENT_ID);

        assertEquals(SeckillResultVO.STATUS_PROCESSING, result.getStatus());
        assertNull(result.getFailReason());
    }

    @Test
    @DisplayName("缺少 requestId 时直接返回 FAILED，避免前端拿空标识无限轮询")
    void shouldReturnFailedWhenRequestIdBlank() {
        SeckillResultVO result = studentGiftService.getSeckillResultByRequestId("  ", STUDENT_ID);

        assertEquals(SeckillResultVO.STATUS_FAILED, result.getStatus());
        assertNotNull(result.getFailReason());
    }

    @Test
    @DisplayName("写失败标记：按学生+requestId 维度落 Redis 并带过期时间")
    void shouldWriteFailureMarkerWithTtl() {
        studentGiftService.markSeckillFailed(STUDENT_ID, REQUEST_ID, "礼品库存不足，本次兑换未完成");

        verify(valueOperations, times(1)).set(
                eq("seckill:fail:" + STUDENT_ID + ":" + REQUEST_ID),
                eq("礼品库存不足，本次兑换未完成"),
                any(Duration.class));
    }

    @Test
    @DisplayName("写失败标记时 Redis 异常不影响主流程（只降级为处理中）")
    void shouldNotThrowWhenRedisFails() {
        doThrow(new RuntimeException("redis down"))
                .when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        assertDoesNotThrow(() ->
                studentGiftService.markSeckillFailed(STUDENT_ID, REQUEST_ID, "库存不足"));
    }

    @Test
    @DisplayName("订单落库成功后清掉残留失败标记，避免轮询读到旧失败原因")
    void shouldClearFailureMarkerWhenOrderExists() {
        StudentGift order = new StudentGift();
        order.setId(99L);
        order.setOrderNo("ORD20261028002");
        order.setStudentId(STUDENT_ID);
        order.setGiftId(7L);
        order.setQuantity(1);
        order.setPointsCost(50);
        order.setPickupCode("PK87654321");
        order.setStatus(OrderStatus.PENDING.getCode());

        when(studentGiftMapper.selectOne(any(), anyBoolean())).thenReturn(order);
        when(giftMapper.selectById(7L)).thenReturn(null);
        when(studentMapper.selectCurrentPoints(STUDENT_ID)).thenReturn(700);

        studentGiftService.getSeckillResultByRequestId(REQUEST_ID, STUDENT_ID);

        verify(redis, times(1)).delete("seckill:fail:" + STUDENT_ID + ":" + REQUEST_ID);
    }
}
