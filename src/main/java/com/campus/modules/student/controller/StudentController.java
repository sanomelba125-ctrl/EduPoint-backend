package com.campus.modules.student.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.common.auth.AuthUtil;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import com.campus.modules.student.dto.*;
import com.campus.modules.student.service.StudentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 学生认证、个人中心与管理端档案综合控制器
 * 基础路径: /api/student (由全局 context-path=/api + /student 组成)
 */
@RestController
@RequestMapping("/student")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    /**
     * 移动学生端登录 (学号 + 密码)
     */
    @PostMapping("/login")
    public Result<StudentLoginVO> login(@Valid @RequestBody StudentLoginDTO loginDTO) {
        StudentLoginVO vo = studentService.login(loginDTO);
        return Result.success("登录成功", vo);
    }

    /**
     * 移动学生端退出登录
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        studentService.logout();
        return Result.success("退出成功", null);
    }

    /**
     * 获取当前登录学生个人中心资料
     */
    @SaCheckLogin
    @GetMapping("/info")
    public Result<StudentVO> getInfo() {
        StudentVO vo = studentService.getCurrentStudentInfo();
        return Result.success(vo);
    }

    /**
     * 学生修改个人中心资料
     */
    @SaCheckLogin
    @PutMapping("/profile")
    public Result<StudentVO> updateProfile(@RequestBody StudentUpdateProfileDTO updateDTO) {
        StudentVO vo = studentService.updateProfile(updateDTO);
        return Result.success("修改成功", vo);
    }

    /* ==================== 管理端学生档案管理接口 ==================== */

    /**
     * 管理端分页查询学生档案列表
     * GET /student/list
     */
    @GetMapping("/list")
    @SaCheckLogin
    public Result<IPage<StudentVO>> getStudentList(StudentQueryDTO queryDTO) {
        checkAdminPermission();
        IPage<StudentVO> page = studentService.getStudentPage(queryDTO);
        return Result.success(page);
    }

    /**
     * 管理端录入新学生档案
     * POST /student/create
     */
    @PostMapping("/create")
    @SaCheckLogin
    public Result<StudentVO> createStudent(@Valid @RequestBody StudentCreateDTO createDTO) {
        checkAdminPermission();
        StudentVO vo = studentService.createStudent(createDTO);
        return Result.success("学生档案录入成功", vo);
    }

    /**
     * 管理端修改学生档案信息
     * PUT /student/update
     */
    @PutMapping("/update")
    @SaCheckLogin
    public Result<StudentVO> updateStudent(@Valid @RequestBody StudentAdminUpdateDTO updateDTO) {
        checkAdminPermission();
        StudentVO vo = studentService.updateStudentByAdmin(updateDTO);
        return Result.success("学生档案修改成功", vo);
    }

    /**
     * 管理端冻结/解冻学生账号
     * PUT /student/{id}/status
     */
    @PutMapping("/{id}/status")
    @SaCheckLogin
    public Result<Void> updateStudentStatus(
            @PathVariable Long id,
            @RequestParam(required = false) Integer status,
            @RequestBody(required = false) Map<String, Integer> body) {
        checkAdminPermission();
        if (status == null && body != null && body.containsKey("status")) {
            status = body.get("status");
        }
        if (status == null) {
            status = 1;
        }
        studentService.updateStudentStatus(id, status);
        return Result.success(status == 1 ? "学生账号已恢复正常" : "学生账号已成功冻结", null);
    }

    /**
     * 管理端重置学生密码 (统一初始密码 123456)
     * PUT /student/{id}/reset-password
     */
    @PutMapping("/{id}/reset-password")
    @SaCheckLogin
    public Result<Void> resetStudentPassword(
            @PathVariable Long id,
            @RequestBody(required = false) StudentResetPasswordDTO dto) {
        checkAdminPermission();
        String newPassword = (dto != null && dto.getNewPassword() != null) ? dto.getNewPassword() : "123456";
        studentService.resetStudentPassword(id, newPassword);
        return Result.success("学生密码已成功重置", null);
    }

    /**
     * 管理端删除/注销学生档案 (逻辑删除)
     * DELETE /student/{id}
     */
    @DeleteMapping("/{id}")
    @SaCheckLogin
    public Result<Void> deleteStudent(@PathVariable Long id) {
        checkAdminPermission();
        studentService.deleteStudent(id);
        return Result.success("学生档案注销成功", null);
    }

    /**
     * 获取系统中所有不重复的有效班级列表
     * GET /student/classes
     */
    @GetMapping("/classes")
    @SaCheckLogin
    public Result<List<String>> getClasses() {
        return Result.success(studentService.getDistinctClasses());
    }

    /**
     * 校验管理端操作员鉴权前缀，防止学生端 Token 越权
     */
    private void checkAdminPermission() {
        AuthUtil.requireAdminId();
    }
}

