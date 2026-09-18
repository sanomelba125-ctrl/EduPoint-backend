package com.campus.modules.admin.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 重置管理员密码请求 DTO
 */
@Data
public class SysUserResetPasswordDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 重置的新密码（默认 123456） */
    private String newPassword = "123456";
}
