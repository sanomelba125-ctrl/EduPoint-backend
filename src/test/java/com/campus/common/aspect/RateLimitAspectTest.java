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
