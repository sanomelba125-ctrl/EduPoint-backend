package com.campus.modules.student.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.campus.common.result.Result;
import com.campus.modules.student.dto.StudentLoginDTO;
import com.campus.modules.student.dto.StudentLoginVO;
import com.campus.modules.student.dto.StudentUpdateProfileDTO;
import com.campus.modules.student.dto.StudentVO;
import com.campus.modules.student.service.StudentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 移动学生端认证与个人中心控制器
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
}
