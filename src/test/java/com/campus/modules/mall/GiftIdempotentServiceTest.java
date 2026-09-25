package com.campus.modules.mall;

import com.campus.modules.mall.dto.GiftExchangeTokenVO;
import com.campus.modules.mall.entity.StudentGift;
import com.campus.modules.mall.mapper.StudentGiftMapper;
import com.campus.modules.mall.service.impl.GiftIdempotentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 礼品兑换幂等与防重 Token 服务单测 (P2-2 治理)
 */
class GiftIdempotentServiceTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOperations;
    private StudentGiftMapper studentGiftMapper;
    private GiftIdempotentServiceImpl idempotentService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        studentGiftMapper = mock(StudentGiftMapper.class);

        when(redis.opsForValue()).thenReturn(valueOperations);

        idempotentService = new GiftIdempotentServiceImpl(redis, studentGiftMapper);
    }

    @Test
    @DisplayName("测试生成防重 Token：验证 Key 维度为 studentId:giftId，前缀 EXT-，TTL 10 分钟")
    void testGenerateExchangeToken_Success() {
        Long studentId = 101L;
        Long giftId = 5L;

        GiftExchangeTokenVO vo = idempotentService.generateExchangeToken(studentId, giftId);

        assertNotNull(vo);
        assertEquals(giftId, vo.getGiftId());
        assertNotNull(vo.getToken());
        assertTrue(vo.getToken().startsWith("EXT-"), "Token 必须以 EXT- 前缀开头");
        assertEquals(600L, vo.getExpireSeconds(), "有效期必须为 600 秒 (10 分钟)");

        // 验证写入 Redis
        String expectedKey = "gift:exchange:token:" + studentId + ":" + giftId;
        verify(valueOperations, times(1)).set(eq(expectedKey), eq(vo.getToken()), eq(Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("测试参数为空时生成 Token 抛出参数异常")
    void testGenerateExchangeToken_NullParams_ShouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> idempotentService.generateExchangeToken(null, 1L));
        assertThrows(IllegalArgumentException.class, () -> idempotentService.generateExchangeToken(1L, null));
    }

    @Test
    @DisplayName("测试 Lua 脚本原子核验并删除 Token：匹配成功返回 1L，核销即焚成功返回 true")
    @SuppressWarnings("unchecked")
    void testVerifyAndConsumeToken_Success() {
        Long studentId = 101L;
        Long giftId = 5L;
        String token = "EXT-abc123456789";

        String expectedKey = "gift:exchange:token:" + studentId + ":" + giftId;
        when(redis.execute(any(RedisScript.class), eq(Collections.singletonList(expectedKey)), eq(token)))
                .thenReturn(1L);

        boolean result = idempotentService.verifyAndConsumeToken(studentId, giftId, token);

        assertTrue(result, "Lua 脚本返回 1L 时必须通过核销");
        verify(redis, times(1)).execute(any(RedisScript.class), eq(Collections.singletonList(expectedKey)), eq(token));
    }

    @Test
    @DisplayName("测试 Lua 脚本原子核验失败：Token 不匹配或已过期被删除，返回 0L，核销失败返回 false")
    @SuppressWarnings("unchecked")
    void testVerifyAndConsumeToken_ExpiredOrMismatch() {
        Long studentId = 101L;
        Long giftId = 5L;
        String token = "EXT-wrongtoken";

        String expectedKey = "gift:exchange:token:" + studentId + ":" + giftId;
        when(redis.execute(any(RedisScript.class), eq(Collections.singletonList(expectedKey)), eq(token)))
                .thenReturn(0L);

        boolean result = idempotentService.verifyAndConsumeToken(studentId, giftId, token);

        assertFalse(result, "Lua 脚本返回 0L 时必须拦截阻止下单");
    }

    @Test
    @DisplayName("测试防重 Token 为空或空白字符串时直接拦截返回 false")
    void testVerifyAndConsumeToken_BlankToken_ShouldReturnFalse() {
        assertFalse(idempotentService.verifyAndConsumeToken(101L, 1L, null));
        assertFalse(idempotentService.verifyAndConsumeToken(101L, 1L, "   "));
        verifyNoInteractions(redis);
    }

    @Test
    @DisplayName("测试 Redis 异常时防重 Token 核验优雅降级放行")
    @SuppressWarnings("unchecked")
    void testVerifyAndConsumeToken_RedisException_DegradesToTrue() {
        Long studentId = 101L;
        Long giftId = 5L;
        String token = "EXT-abc123456789";

        when(redis.execute(any(RedisScript.class), anyList(), anyString()))
                .thenThrow(new RuntimeException("Redis connection timeout"));

        boolean result = idempotentService.verifyAndConsumeToken(studentId, giftId, token);

        assertTrue(result, "Redis 不可用时应降级放行，由后续 DB 联合唯一索引兜底");
    }

    @Test
    @DisplayName("测试 SETNX 槽位申请成功：首次提交抢到锁")
    void testTryAcquireSlot_Success() {
        Long studentId = 102L;
        String requestId = "REQ-1001";
        String expectedKey = "gift:exchange:idem:" + studentId + ":" + requestId;

        when(valueOperations.setIfAbsent(eq(expectedKey), eq("PENDING"), eq(Duration.ofSeconds(30))))
                .thenReturn(true);

        assertTrue(idempotentService.tryAcquireSlot(studentId, requestId));
    }

    @Test
    @DisplayName("测试 SETNX 槽位冲突：并发二次提交被拦截")
    void testTryAcquireSlot_Conflict() {
        Long studentId = 102L;
        String requestId = "REQ-1001";
        String expectedKey = "gift:exchange:idem:" + studentId + ":" + requestId;

        when(valueOperations.setIfAbsent(eq(expectedKey), eq("PENDING"), eq(Duration.ofSeconds(30))))
                .thenReturn(false);

        assertFalse(idempotentService.tryAcquireSlot(studentId, requestId));
    }

    @Test
    @DisplayName("测试标记槽位完成：写入 DONE:orderId，保留 24 小时")
    void testMarkSlotDone() {
        Long studentId = 103L;
        String requestId = "REQ-1002";
        Long orderId = 9999L;
        String expectedKey = "gift:exchange:idem:" + studentId + ":" + requestId;

        idempotentService.markSlotDone(studentId, requestId, orderId);

        verify(valueOperations, times(1)).set(eq(expectedKey), eq("DONE:" + orderId), eq(Duration.ofHours(24)));
    }

    @Test
    @DisplayName("测试基于 (student_id, request_id) 联合维度反查既有订单")
    void testFindExistingOrder_HitsCompositeDimension() {
        Long studentId = 104L;
        String requestId = "REQ-1003";

        StudentGift mockOrder = new StudentGift();
        mockOrder.setId(1234L);
        mockOrder.setOrderNo("ORD202609230001");
        mockOrder.setStudentId(studentId);
        mockOrder.setRequestId(requestId);

        when(studentGiftMapper.selectOne(any(), eq(false))).thenReturn(mockOrder);

        StudentGift found = idempotentService.findExistingOrder(studentId, requestId);

        assertNotNull(found);
        assertEquals("ORD202609230001", found.getOrderNo());
        assertEquals(studentId, found.getStudentId());
        assertEquals(requestId, found.getRequestId());
    }
}
