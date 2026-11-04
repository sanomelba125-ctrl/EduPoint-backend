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
        assertTrue(includePatterns.contains("/actuator/prometheus/**"), "必须对 /actuator/prometheus/** 强制鉴权");
        assertTrue(includePatterns.contains("/actuator/metrics"), "必须对 /actuator/metrics 强制鉴权");
        assertTrue(includePatterns.contains("/actuator/metrics/**"), "必须对 /actuator/metrics/** 强制鉴权");
    }

    @Test
    @DisplayName("F10 核验：Actuator 拦截器必须要求管理员身份，阻断未登录与学生端访问，放行管理员")
    @SuppressWarnings("unchecked")
    void actuatorInterceptor_mustRequireAdminRole() {
        SaTokenConfig config = new SaTokenConfig();
        InterceptorRegistry registry = new InterceptorRegistry();
        config.addInterceptors(registry);

        List<InterceptorRegistration> registrations = (List<InterceptorRegistration>)
                ReflectionTestUtils.getField(registry, "registrations");
        assertNotNull(registrations);
        InterceptorRegistration actuatorReg = registrations.get(1);
        SaInterceptor saInterceptor = (SaInterceptor) ReflectionTestUtils.getField(actuatorReg, "interceptor");
        assertNotNull(saInterceptor);
        cn.dev33.satoken.fun.SaParamFunction<Object> auth =
                (cn.dev33.satoken.fun.SaParamFunction<Object>) ReflectionTestUtils.getField(saInterceptor, "auth");
        assertNotNull(auth, "Actuator 拦截器必须配置专属鉴权函数");

        // 初始化 SaToken 上下文及 Spring 请求上下文
        cn.dev33.satoken.SaManager.setSaTokenContext(new cn.dev33.satoken.spring.SaTokenContextForSpringInJakartaServlet());
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        org.springframework.mock.web.MockHttpServletResponse response = new org.springframework.mock.web.MockHttpServletResponse();
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(request, response));

        try {
            // 1. 未登录时必须拦截并抛出未登录异常
            assertThrows(cn.dev33.satoken.exception.NotLoginException.class, () -> auth.run(null));

            // 2. 学生端账号登录时必须拦截并抛出 403 Forbidden 业务异常
            cn.dev33.satoken.stp.StpUtil.login("student:1001");
            com.campus.common.exception.BusinessException ex =
                    assertThrows(com.campus.common.exception.BusinessException.class, () -> auth.run(null));
            assertEquals(com.campus.common.result.ResultCode.FORBIDDEN.getCode(), ex.getCode());

            // 3. 管理端账号登录时必须成功放行
            cn.dev33.satoken.stp.StpUtil.logout();
            cn.dev33.satoken.stp.StpUtil.login("admin:1");
            assertDoesNotThrow(() -> auth.run(null));
        } finally {
            cn.dev33.satoken.stp.StpUtil.logout();
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
    }
}
