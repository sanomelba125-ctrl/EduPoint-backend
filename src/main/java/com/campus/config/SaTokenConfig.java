package com.campus.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import com.campus.common.auth.AuthUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 路由与安全拦截器配置 (P1-B/N1/F10)
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

        // 2. 显式对敏感监控端点（Prometheus/Metrics 等）加装强制管理员权限鉴权拦截器 (N1/F10 修复)
        // 彻底杜绝普通学生或未授权用户越权访问内部监控指标与 Prometheus 抓取端点
        registry.addInterceptor(new SaInterceptor(handle -> AuthUtil.requireAdminId()))
                .addPathPatterns(
                        "/actuator/prometheus",
                        "/actuator/prometheus/**",
                        "/actuator/metrics",
                        "/actuator/metrics/**"
                );
    }
}
