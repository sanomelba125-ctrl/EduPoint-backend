package com.campus.common.constant;

/**
 * 平台核心安全与认证常量
 */
public final class SecurityConstants {

    private SecurityConstants() {
        // 私有构造，防止实例化
    }

    /**
     * 统一系统管理员与学生账号重置初始默认密码 (弱密码仅供管理员重置后通知用户首次登录改密)
     */
    public static final String DEFAULT_INITIAL_PASSWORD = "123456";
}
