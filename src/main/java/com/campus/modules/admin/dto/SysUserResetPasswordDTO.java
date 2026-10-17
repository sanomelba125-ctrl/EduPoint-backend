package com.campus.modules.admin.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 重置管理员密码请求 DTO
 */
@Data
public class SysUserResetPasswordDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 重置的新密码（若未传入则系统默认重置为配置的初始默认口令） */
    private String newPassword;
}
