package com.campus.modules.admin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.common.result.Result;
import com.campus.modules.admin.dto.*;
import com.campus.modules.admin.service.SysUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 系统用户与管理员认证及管理控制器
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

    /**
     * 分页查询管理员列表
     * GET /user/list
     */
    @GetMapping("/list")
    @SaCheckLogin
    public Result<IPage<SysUserVO>> getUserList(SysUserQueryDTO queryDTO) {
        IPage<SysUserVO> page = sysUserService.getUserPage(queryDTO);
        return Result.success(page);
    }

    /**
     * 新增管理员账号
     * POST /user/create
     */
    @PostMapping("/create")
    @SaCheckLogin
    public Result<SysUserVO> createUser(@Valid @RequestBody SysUserCreateDTO createDTO) {
        SysUserVO vo = sysUserService.createUser(createDTO);
        return Result.success("管理员账号创建成功", vo);
    }

    /**
     * 修改管理员信息
     * PUT /user/update
     */
    @PutMapping("/update")
    @SaCheckLogin
    public Result<SysUserVO> updateUser(@Valid @RequestBody SysUserUpdateDTO updateDTO) {
        SysUserVO vo = sysUserService.updateUser(updateDTO);
        return Result.success("管理员信息更新成功", vo);
    }

    /**
     * 启用/停用管理员账号
     * PUT /user/{id}/status
     */
    @PutMapping("/{id}/status")
    @SaCheckLogin
    public Result<Void> updateUserStatus(
            @PathVariable Long id,
            @RequestParam(required = false) Integer status,
            @RequestBody(required = false) Map<String, Integer> body) {
        if (status == null && body != null && body.containsKey("status")) {
            status = body.get("status");
        }
        if (status == null) {
            status = 1;
        }
        sysUserService.updateUserStatus(id, status);
        return Result.success(status == 1 ? "账号已启用" : "账号已停用", null);
    }

    /**
     * 删除/注销管理员账号 (逻辑删除)
     * DELETE /user/{id}
     */
    @DeleteMapping("/{id}")
    @SaCheckLogin
    public Result<Void> deleteUser(@PathVariable Long id) {
        String loginId = StpUtil.getLoginIdAsString();
        Long currentUserId = null;
        if (loginId != null && loginId.startsWith("admin:")) {
            currentUserId = Long.parseLong(loginId.substring("admin:".length()));
        }
        sysUserService.deleteUser(id, currentUserId);
        return Result.success("管理员账号注销成功", null);
    }

    /**
     * 重置管理员密码
     * PUT /user/{id}/reset-password
     */
    @PutMapping("/{id}/reset-password")
    @SaCheckLogin
    public Result<Void> resetPassword(
            @PathVariable Long id,
            @RequestBody(required = false) SysUserResetPasswordDTO dto) {
        String newPassword = (dto != null && dto.getNewPassword() != null) ? dto.getNewPassword() : "123";
        sysUserService.resetPassword(id, newPassword);
        return Result.success("管理员密码重置成功", null);
    }
}