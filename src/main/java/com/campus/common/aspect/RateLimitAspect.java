package com.campus.common.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.campus.common.annotation.RateLimit;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import org.aspectj.lang.reflect.MethodSignature;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.UUID;

/**
 * 分布式限流切面 (P1-6 基于 Redis + Lua 滑动窗口算法)
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final StringRedisTemplate redis;

    /**
     * 默认不信任客户端自报的 X-Forwarded-For，避免伪造 IP 绕过登录限流。
     * 仅当应用部署在可信反向代理之后，或本地隔离压测时，才显式开启。
     */
    @org.springframework.beans.factory.annotation.Value("${campus.security.trust-forwarded-headers:false}")
    private boolean trustForwardedHeaders;
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;

    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setLocation(new ClassPathResource("lua/rate_limit_sliding_window.lua"));
        RATE_LIMIT_SCRIPT.setResultType(Long.class);
    }

    @Around("@annotation(com.campus.common.annotation.RateLimit) || @annotation(com.campus.common.annotation.RateLimits)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit[] rateLimits = method.getAnnotationsByType(RateLimit.class);
        if (rateLimits != null && rateLimits.length > 0) {
            for (RateLimit rateLimit : rateLimits) {
                checkRateLimit(joinPoint, rateLimit);
            }
        }
        return joinPoint.proceed();
    }

    private void checkRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
        String key = buildLimitKey(joinPoint, rateLimit);
        long windowMs = rateLimit.windowMs();
        int maxCount = rateLimit.count();
        long now = System.currentTimeMillis();
        String memberId = UUID.randomUUID().toString();

        try {
            Long result = redis.execute(
                    RATE_LIMIT_SCRIPT,
                    Collections.singletonList(key),
                    String.valueOf(windowMs),
                    String.valueOf(maxCount),
                    String.valueOf(now),
                    memberId
            );

            if (result == null || result != 1L) {
                log.warn("接口触发限流拦截: key={}, windowMs={}, maxCount={}", key, windowMs, maxCount);
                throw new BusinessException(ResultCode.TOO_MANY_REQUESTS.getCode(), rateLimit.message());
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Redis 限流脚本执行异常, key={}", key, e);
            // 默认限流故障弱化为放行以保证基础可用性，或者在关键金融/秒杀链路记录告警
        }
    }

    private String buildLimitKey(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
        StringBuilder sb = new StringBuilder(rateLimit.key());
        if (rateLimit.limitType() == RateLimit.LimitType.USER) {
            try {
                if (StpUtil.isLogin()) {
                    sb.append(":user:").append(StpUtil.getLoginIdAsString());
                } else {
                    sb.append(":user:anonymous");
                }
            } catch (Exception e) {
                sb.append(":user:unknown");
            }
        } else if (rateLimit.limitType() == RateLimit.LimitType.IP) {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String ip = null;
                if (trustForwardedHeaders) {
                    ip = request.getHeader("X-Forwarded-For");
                    if (ip != null && !ip.isBlank()) {
                        ip = ip.split(",")[0].trim();
                    } else {
                        ip = request.getHeader("X-Real-IP");
                    }
                }
                if (ip == null || ip.isBlank()) {
                    ip = request.getRemoteAddr();
                }
                sb.append(":ip:").append(ip);
            }
        }
        return sb.toString();
    }
}
