package com.campus.modules.student.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理端修改学生档案信息请求 DTO
 */
@Data
public class StudentAdminUpdateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 学生主键 ID */
    @NotNull(message = "学生 ID 不能为空")
    private Long id;

    /** 学生姓名 */
    @Size(max = 50, message = "姓名长度不能超过 50 个字符")
    private String name;

    /** 性别: 1-男, 0-女 */
    private Integer gender;

    /** 班级名称 */
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

    /** 账号状态: 0-冻结/禁用, 1-正常启用 */
    private Integer status;

    /** 选填新密码（若不传则不修改） */
    private String password;
}
