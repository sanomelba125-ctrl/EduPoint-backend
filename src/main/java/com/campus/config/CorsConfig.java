package com.campus.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * 跨域资源共享配置 (P5-2 收敛 CORS 泛域名白名单治理)
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${campus.cors.allowed-origins:http://localhost:3000,http://localhost:3200,http://localhost:5173,http://127.0.0.1:3000,http://127.0.0.1:3200,http://127.0.0.1:5173}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);

        registry.addMapping("/**")
                .allowedOriginPatterns(origins.length > 0 ? origins : new String[]{"*"})
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition", "satoken", "X-Trace-Id", "traceId")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
