package com.campus.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Actuator 监控端点鉴权防护配置测试 (N1 修复核验)")
class ActuatorSecurityTest {

    @Test
    @DisplayName("N1 核验：SaTokenConfig 必须注册 Actuator 强制鉴权拦截器，严防未授权裸奔")
    @SuppressWarnings("unchecked")
    void saTokenConfig_mustRegisterActuatorSecurityInterceptor() {
        SaTokenConfig config = new SaTokenConfig();
        InterceptorRegistry registry = new InterceptorRegistry();
        config.addInterceptors(registry);

        List<InterceptorRegistration> registrations = (List<InterceptorRegistration>)
                ReflectionTestUtils.getField(registry, "registrations");

        assertNotNull(registrations, "拦截器注册列表不应为空");
        assertEquals(2, registrations.size(), "必须注册 2 个拦截器 (基础注解拦截器 + Actuator 专属鉴权拦截器)");

        // 验证第二个拦截器（Actuator 监控专有）
        InterceptorRegistration actuatorReg = registrations.get(1);
        Object interceptor = ReflectionTestUtils.getField(actuatorReg, "interceptor");
        assertNotNull(interceptor);
        assertTrue(interceptor instanceof SaInterceptor, "必须是 Sa-Token 路由拦截器");

        // 验证保护路径包含 /actuator/prometheus 和 /actuator/metrics
        List<String> includePatterns = (List<String>) ReflectionTestUtils.getField(actuatorReg, "includePatterns");
        assertNotNull(includePatterns);
        assertTrue(includePatterns.contains("/actuator/prometheus"), "必须对 /actuator/prometheus 强制鉴权");
        assertTrue(includePatterns.contains("/actuator/metrics"), "必须对 /actuator/metrics 强制鉴权");
    }
}
