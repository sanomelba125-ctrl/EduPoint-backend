package com.campus.modules.activity.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

/**
 * Activity 控制器
 * 负责处理 HTTP 请求与响应
 */
@RestController
@RequestMapping("/api/activity/")
@RequiredArgsConstructor
public class ActivityController {
    // 注入 Service
    // private final ActivityService service;
}
