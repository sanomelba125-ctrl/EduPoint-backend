package com.campus.modules.student.dto;

import com.campus.common.constant.SecurityConstants;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理端重置学生密码请求 DTO
 */
@Data
public class StudentResetPasswordDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 重置的新密码（统一默认 123456） */
    private String newPassword = SecurityConstants.DEFAULT_INITIAL_PASSWORD;
}
