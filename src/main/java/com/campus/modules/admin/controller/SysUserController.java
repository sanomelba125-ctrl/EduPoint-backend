package com.campus.modules.admin.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

/**
 * SysUser 控制器
 * 负责处理 HTTP 请求与响应
 */
@RestController
@RequestMapping("/api/admin/")
@RequiredArgsConstructor
public class SysUserController {
    // 注入 Service
    // private final SysUserService service;
}
