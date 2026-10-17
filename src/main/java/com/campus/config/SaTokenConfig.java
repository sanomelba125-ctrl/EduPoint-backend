package com.campus.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 路由与安全拦截器配置 (P1-B/N1)
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 注册基础注解鉴权拦截器，激活 @SaCheckLogin, @SaCheckRole 等方法注解
        registry.addInterceptor(new SaInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/swagger-resources/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/favicon.ico",
                        "/actuator/health",
                        "/actuator/info"
                );

        // 2. 显式对敏感监控端点（Prometheus/Metrics 等）加装强制登录鉴权拦截器 (N1 修复)
        // 彻底杜绝无参 SaInterceptor 因 Actuator 非自定义 Controller 无注解而导致的鉴权穿透与裸奔
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns(
                        "/actuator/prometheus",
                        "/actuator/metrics",
                        "/actuator/metrics/**"
                );
    }
}
