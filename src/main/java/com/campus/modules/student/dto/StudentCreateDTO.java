package com.campus.modules.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理端录入新学生档案请求 DTO
 */
@Data
public class StudentCreateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 学生学号（唯一标识账号） */
    @NotBlank(message = "学生学号不能为空")
    @Size(min = 3, max = 30, message = "学号长度需在 3 到 30 个字符之间")
    private String studentNo;

    /** 学生真实姓名 */
    @NotBlank(message = "学生姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过 50 个字符")
    private String name;

    /** 性别: 1-男, 0-女 (或 2-女，默认 1) */
    private Integer gender = 1;

    /** 所属班级 */
    @NotBlank(message = "所属班级不能为空")
    @Size(max = 50, message = "班级名称长度不能超过 50 个字符")
    private String className;

    /** 所属学院 */
    private String college;

    /** 所学专业 */
    private String major;

    /** 联系电话 */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 学生头像 URL */
    private String avatar;

    /** 初始登录密码（选填，默认 123456） */
    private String password;
}
