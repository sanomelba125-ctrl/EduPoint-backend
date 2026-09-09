package com.campus.modules.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 修改后台管理员信息请求 DTO
 */
@Data
public class SysUserUpdateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 管理员主键 ID */
    @NotNull(message = "管理员 ID 不能为空")
    private Long id;

    /** 管理员姓名/昵称 */
    @Size(max = 50, message = "姓名长度不能超过 50 个字符")
    private String nickname;

    /** 角色权限: SUPER_ADMIN-超级管理员, ADMIN-普通管理员, COUNSELOR-辅导员 */
    private String role;

    /** 联系手机号 */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 用户头像 URL */
    private String avatar;

    /** 账号状态: 0-禁用 1-启用 */
    private Integer status;

    /** 可选新密码（若不传则不修改密码） */
    private String password;
}
