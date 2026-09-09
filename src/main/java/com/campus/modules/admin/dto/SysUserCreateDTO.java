package com.campus.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 新增后台管理员请求 DTO
 */
@Data
public class SysUserCreateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 登录用户名(唯一账号) */
    @NotBlank(message = "登录账号不能为空")
    @Size(min = 3, max = 30, message = "账号长度需在 3-30 个字符之间")
    private String username;

    /** 登录密码(若不填默认 123) */
    private String password;

    /** 管理员姓名/昵称 */
    @NotBlank(message = "管理员姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过 50 个字符")
    private String nickname;

    /** 角色权限: SUPER_ADMIN-超级管理员, ADMIN-普通管理员, COUNSELOR-辅导员 */
    @NotBlank(message = "角色权限不能为空")
    private String role;

    /** 联系手机号 */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 用户头像 URL */
    private String avatar;
}
