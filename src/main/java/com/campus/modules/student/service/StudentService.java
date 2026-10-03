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

    /**
     * 学生自助修改个人登录密码
     *
     * @param dto 修改密码入参
     */
    void changePassword(com.campus.modules.student.dto.StudentChangePasswordDTO dto);

    /**
     * 管理端分页多条件查询学生档案列表
     *
     * @param queryDTO 查询入参
     * @return 分页学生列表
     */
    com.baomidou.mybatisplus.core.metadata.IPage<StudentVO> getStudentPage(com.campus.modules.student.dto.StudentQueryDTO queryDTO);

    /**
     * 管理端录入新学生档案
     *
     * @param createDTO 新增入参
     * @return 新建的学生脱敏档案
     */
    StudentVO createStudent(com.campus.modules.student.dto.StudentCreateDTO createDTO);

    /**
     * 管理端修改学生基本档案
     *
     * @param updateDTO 修改入参
     * @return 更新后的学生脱敏档案
     */
    StudentVO updateStudentByAdmin(com.campus.modules.student.dto.StudentAdminUpdateDTO updateDTO);

    /**
     * 管理端冻结/解冻学生账号
     *
     * @param id     学生ID
     * @param status 目标状态 (0-冻结/禁用, 1-正常启用)
     */
    void updateStudentStatus(Long id, Integer status);

    /**
     * 管理端重置学生登录密码
     *
     * @param id          学生ID
     * @param newPassword 新密码
     */
    void resetStudentPassword(Long id, String newPassword);

    /**
     * 管理端删除/注销学生档案(逻辑删除)
     *
     * @param id 学生ID
     */
    void deleteStudent(Long id);

    /**
     * 获取系统中所有不重复的有效班级名称列表
     *
     * @return 班级名称列表
     */
    java.util.List<String> getDistinctClasses();
}
