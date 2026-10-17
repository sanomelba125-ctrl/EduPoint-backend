package com.campus.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * 跨域资源共享配置 (Fail-Closed 严格白名单治理)
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${campus.cors.allowed-origins:http://localhost:3000,http://localhost:3200,http://localhost:5173,http://127.0.0.1:3000,http://127.0.0.1:3200,http://127.0.0.1:5173}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = allowedOrigins != null
                ? Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .toArray(String[]::new)
                : new String[0];

        // [架构设计 - Fail-Closed 安全不变量]
        // 当白名单配置为空或无效时拒绝注册带凭证的通用通配符，防止击穿浏览器同源与凭证隔离策略
        if (origins.length == 0) {
            throw new IllegalStateException("CORS 白名单配置异常: campus.cors.allowed-origins 必须配置明确来源 (Fail-Closed 禁止通配符凭据跨域)");
        }

        registry.addMapping("/**")
                .allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition", "satoken", "X-Trace-Id", "traceId")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
