package com.campus.modules.admin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.campus.common.result.Result;
import com.campus.modules.admin.dto.AdminLoginDTO;
import com.campus.modules.admin.dto.AdminLoginVO;
import com.campus.modules.admin.dto.SysUserVO;
import com.campus.modules.admin.service.SysUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 系统用户与管理员认证控制器
 * 支持 /user 与 /auth 双重前缀路由，契合 API.md 规范与现有前端调用
 */
@RestController
@RequestMapping({"/user", "/auth"})
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;

    /**
     * 管理员登录
     * 对应 API.md 1.1 /user/login 及 admin-web /auth/login
     */
    @PostMapping("/login")
    public Result<AdminLoginVO> login(@Valid @RequestBody AdminLoginDTO loginDTO) {
        AdminLoginVO vo = sysUserService.login(loginDTO);
        return Result.success("登录成功", vo);
    }

    /**
     * 管理员退出登录
     * 对应 API.md 1.2 /user/logout 及 admin-web /auth/logout
     */
    @PostMapping("/logout")
    @SaCheckLogin
    public Result<Void> logout() {
        sysUserService.logout();
        return Result.success("退出成功", null);
    }

    /**
     * 获取当前登录管理员信息
     * 对应 API.md 1.3 /user/info 及 admin-web /auth/me
     */
    @GetMapping({"/info", "/me"})
    @SaCheckLogin
    public Result<SysUserVO> getCurrentUser() {
        SysUserVO vo = sysUserService.getCurrentUserInfo();
        return Result.success(vo);
    }
}