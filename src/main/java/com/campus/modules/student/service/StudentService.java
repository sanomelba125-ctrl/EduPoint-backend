package com.campus.modules.student.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.modules.student.dto.StudentLoginDTO;
import com.campus.modules.student.dto.StudentLoginVO;
import com.campus.modules.student.dto.StudentUpdateProfileDTO;
import com.campus.modules.student.dto.StudentVO;
import com.campus.modules.student.entity.Student;

/**
 * 学生服务接口
 */
public interface StudentService extends IService<Student> {

    /**
     * 移动学生端登录 (学号 + 密码)
     *
     * @param loginDTO 登录入参
     * @return 登录结果 (Token 及学生脱敏档案)
     */
    StudentLoginVO login(StudentLoginDTO loginDTO);

    /**
     * 移动学生端登出 (销毁会话)
     */
    void logout();

    /**
     * 获取当前登录学生个人信息
     *
     * @return 学生脱敏档案
     */
    StudentVO getCurrentStudentInfo();

    /**
     * 学生修改个人中心资料
     *
     * @param updateDTO 修改入参
     * @return 修改后的学生脱敏档案
     */
    StudentVO updateProfile(StudentUpdateProfileDTO updateDTO);
}
