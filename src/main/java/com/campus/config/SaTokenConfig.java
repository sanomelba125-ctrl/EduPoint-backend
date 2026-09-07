package com.campus.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 注解拦截器配�? */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Sa-Token 拦截器，�?@SaCheckLogin, @SaCheckRole, @SaCheckPermission 等注解生�?        registry.addInterceptor(new SaInterceptor()).addPathPatterns("/**");
    }
}
