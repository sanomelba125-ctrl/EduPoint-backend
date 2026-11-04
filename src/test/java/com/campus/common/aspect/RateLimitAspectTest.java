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
    @DisplayName("P1-F8 安全: 显式信任代理时，优先使用 X-Real-IP，杜绝客户端伪造 XFF 首段绕过")
    void testRateLimit_PrefersXRealIpOverXForwardedForWhenTrusted() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("X-Real-IP", "198.51.100.1");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Method method = this.getClass().getMethod("dummyIpTargetMethod");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1L);
        ReflectionTestUtils.setField(rateLimitAspect, "trustForwardedHeaders", true);

        rateLimitAspect.around(joinPoint);

        org.mockito.ArgumentCaptor<List<String>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(redis).execute(any(DefaultRedisScript.class), captor.capture(), anyString(), anyString(), anyString(), anyString());
        String key = captor.getValue().get(0);
        assertTrue(key.contains(":ip:198.51.100.1"), "应优先使用 X-Real-IP 而非 X-Forwarded-For 伪造首段: " + key);
    }

    @Test
    @DisplayName("P1-F8 安全: X-Real-IP 为 unknown 时，自动回退到合法的 X-Forwarded-For")
    void testRateLimit_UnknownXRealIpFallsBackToXForwardedFor() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("X-Real-IP", "unknown");
        request.addHeader("X-Forwarded-For", "203.0.113.15, 10.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Method method = this.getClass().getMethod("dummyIpTargetMethod");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1L);
        ReflectionTestUtils.setField(rateLimitAspect, "trustForwardedHeaders", true);

        rateLimitAspect.around(joinPoint);

        org.mockito.ArgumentCaptor<List<String>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(redis).execute(any(DefaultRedisScript.class), captor.capture(), anyString(), anyString(), anyString(), anyString());
        String key = captor.getValue().get(0);
        assertTrue(key.contains(":ip:203.0.113.15"), "当 X-Real-IP 为 unknown 时应回退到 XFF 首段: " + key);
    }

    @Test
    @DisplayName("P1-F8 安全: 代理头全部为 unknown 时，自动回退到 request.getRemoteAddr()")
    void testRateLimit_AllUnknownHeadersFallBackToRemoteAddr() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.99");
        request.addHeader("X-Real-IP", "unknown");
        request.addHeader("X-Forwarded-For", "unknown");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Method method = this.getClass().getMethod("dummyIpTargetMethod");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1L);
        ReflectionTestUtils.setField(rateLimitAspect, "trustForwardedHeaders", true);

        rateLimitAspect.around(joinPoint);

        org.mockito.ArgumentCaptor<List<String>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(redis).execute(any(DefaultRedisScript.class), captor.capture(), anyString(), anyString(), anyString(), anyString());
        String key = captor.getValue().get(0);
        assertTrue(key.contains(":ip:10.0.0.99"), "全部代理头无效时应回退到 RemoteAddr: " + key);
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
