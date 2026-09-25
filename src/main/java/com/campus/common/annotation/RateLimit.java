package com.campus.common.annotation;

import java.lang.annotation.*;

/**
 * 分布式限流注解 (P1-6 基于 Redis + Lua 滑动窗口)
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(RateLimits.class)
public @interface RateLimit {

    /**
     * 限流业务前缀 Key
     */
    String key() default "rate_limit:default";

    /**
     * 窗口大小 (毫秒)，默认 1000ms (1秒)
     */
    long windowMs() default 1000L;

    /**
     * 窗口内最大允许请求数
     */
    int count() default 100;

    /**
     * 限流维度
     */
    LimitType limitType() default LimitType.DEFAULT;

    /**
     * 超限提示文案
     */
    String message() default "当前访问过于频繁或排队人数较多，请稍后重试！";

    enum LimitType {
        /** 全局接口维度 */
        DEFAULT,
        /** 按当前客户端 IP 维度 */
        IP,
        /** 按当前登录学生/用户 ID 维度 */
        USER
    }
}
