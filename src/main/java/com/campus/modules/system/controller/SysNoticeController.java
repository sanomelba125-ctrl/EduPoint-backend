package com.campus.modules.system.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

/**
 * SysNotice 控制器
 * 负责处理 HTTP 请求与响应
 */
@RestController
@RequestMapping("/api/system/")
@RequiredArgsConstructor
public class SysNoticeController {
    // 注入 Service
    // private final SysNoticeService service;
}
