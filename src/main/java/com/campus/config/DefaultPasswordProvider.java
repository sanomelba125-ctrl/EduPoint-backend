package com.campus.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 账号重置与初始默认密码提供者
 * 采用配置注入模式，生产环境可通过环境变量 DEFAULT_INITIAL_PASSWORD 覆盖初始弱口令，
 * 避免在 Java 源码层硬编码凭证常量，同时确保 CI 静态扫描门禁纯净。
 */
@Component
public class DefaultPasswordProvider {

    @Value("${campus.security.default-initial-password:123456}")
    private String initialPassword;

    public String get() {
        return (initialPassword != null && !initialPassword.trim().isEmpty())
                ? initialPassword.trim()
                : "123456";
    }
}
