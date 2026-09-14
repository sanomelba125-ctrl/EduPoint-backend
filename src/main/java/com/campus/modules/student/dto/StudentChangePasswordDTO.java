package com.campus.modules.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 学生自助修改密码请求 DTO
 */
@Data
public class StudentChangePasswordDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 原登录密码
     */
    @NotBlank(message = "请输入当前原密码")
    private String oldPassword;

    /**
     * 新密码
     */
    @NotBlank(message = "请输入新密码")
    @Size(min = 6, max = 32, message = "新密码长度需在 6 到 32 个字符之间")
    private String newPassword;

    /**
     * 确认新密码
     */
    @NotBlank(message = "请再次输入新密码确认")
    private String confirmPassword;
}
