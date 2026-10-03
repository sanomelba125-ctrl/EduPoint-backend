package com.campus.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理员登录成功响应视图载体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminLoginVO {

    /** 会话访问令牌 */
    private String token;

    /** 管理员用户信息 (契合 API.md 规范) */
    private SysUserVO user;

    /** 兼容 admin-web 前端现有命名结构 */
    private SysUserVO userInfo;
}
