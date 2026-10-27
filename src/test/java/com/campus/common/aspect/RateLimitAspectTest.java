package com.campus.common.aspect;

import com.campus.common.annotation.RateLimit;
import com.campus.common.exception.BusinessException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 分布式限流切面 (P1-6) 单测
 */
@ExtendWith(MockitoExtension.class)
public class RateLimitAspectTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private MethodSignature methodSignature;

    private RateLimitAspect rateLimitAspect;

    @BeforeEach
    void setUp() {
        rateLimitAspect = new RateLimitAspect(redis);
    }

    // 辅助测试靶标方法
    @RateLimit(key = "rate_limit:test:api", count = 10, windowMs = 1000, message = "触发限流")
    public void dummyTargetMethod() {
    }

    @RateLimit(key = "rate_limit:test:ip", count = 10, windowMs = 1000, limitType = RateLimit.LimitType.IP)
    public void dummyIpTargetMethod() {
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @SuppressWarnings("unchecked")
    private String capturedIpKey(String methodName, boolean trustForwarded) throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Method method = this.getClass().getMethod(methodName);
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1L);
        ReflectionTestUtils.setField(rateLimitAspect, "trustForwardedHeaders", trustForwarded);

        rateLimitAspect.around(joinPoint);

        org.mockito.ArgumentCaptor<List<String>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(redis).execute(any(DefaultRedisScript.class), captor.capture(), anyString(), anyString(), anyString(), anyString());
        return captor.getValue().get(0);
    }

    @Test
    @DisplayName("P1-6 安全: 默认不信任 X-Forwarded-For，限流按真实 remoteAddr")
    void testRateLimit_IgnoresForwardedHeaderByDefault() throws Throwable {
        String key = capturedIpKey("dummyIpTargetMethod", false);
        assertTrue(key.contains(":ip:10.0.0.8"));
    }

    @Test
    @DisplayName("P1-6 安全: 显式信任代理后才使用 X-Forwarded-For 首段 IP")
    void testRateLimit_UsesForwardedHeaderWhenTrusted() throws Throwable {
        String key = capturedIpKey("dummyIpTargetMethod", true);
        assertTrue(key.contains(":ip:203.0.113.9"));
    }

    @Test
    @DisplayName("P1-6: 滑动窗口限流 - 窗口内未超限 (Lua返回1) -> 成功放行")
    void testRateLimit_Allowed() throws Throwable {
        Method method = this.getClass().getMethod("dummyTargetMethod");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.proceed()).thenReturn("SUCCESS");

        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1L);

        Object result = rateLimitAspect.around(joinPoint);

        assertEquals("SUCCESS", result);
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    @DisplayName("P1-6: 滑动窗口限流 - 窗口内超限 (Lua返回0) -> 抛出 BusinessException 阻断请求")
    void testRateLimit_Blocked() throws NoSuchMethodException {
        Method method = this.getClass().getMethod("dummyTargetMethod");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);

        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class, () -> rateLimitAspect.around(joinPoint));
        assertEquals("触发限流", ex.getMessage());
        assertEquals(429, ex.getCode());

        try {
            verify(joinPoint, never()).proceed();
        } catch (Throwable ignored) {}
    }

    @Test
    @DisplayName("P1-6: 高可用容灾 - Redis 异常时软降级放行，保证基础业务可用")
    void testRateLimit_RedisErrorDegrade() throws Throwable {
        Method method = this.getClass().getMethod("dummyTargetMethod");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.proceed()).thenReturn("DEGRADED_PASS");

        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Redis connection refused"));

        Object result = rateLimitAspect.around(joinPoint);

        assertEquals("DEGRADED_PASS", result);
        verify(joinPoint, times(1)).proceed();
    }
}
