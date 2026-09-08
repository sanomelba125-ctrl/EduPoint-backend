package com.campus.modules.student.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 移动学生端登录成功返回视图对象
 * 同时提供 student / studentInfo / userInfo 键，100% 兼容 API 规范与移动端 Pinia 状态管理
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentLoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话访问令牌 (Sa-Token)
     */
    private String token;

    /**
     * 学生档案信息 (契合 API.md 1.4: data.student)
     */
    private StudentVO student;

    /**
     * 学生档案信息 (契合 mobile-app 本地缓存 student_info)
     */
    private StudentVO studentInfo;

    /**
     * 学生档案信息 (契合 mobile-app Pinia userInfo)
     */
    private StudentVO userInfo;
}
