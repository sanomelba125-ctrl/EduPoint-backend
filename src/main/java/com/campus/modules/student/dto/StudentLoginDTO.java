package com.campus.modules.student.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 移动学生端登录请求入参
 */
@Data
public class StudentLoginDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 学生学号 (账号)
     */
    @NotBlank(message = "学号不能为空")
    private String studentNo;

    /**
     * 登录密码 (明文)
     */
    @NotBlank(message = "密码不能为空")
    private String password;
}
