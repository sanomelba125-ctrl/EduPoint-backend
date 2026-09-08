package com.campus.modules.mall.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

/**
 * Gift 控制器
 * 负责处理 HTTP 请求与响应
 */
@RestController
@RequestMapping("/api/mall/")
@RequiredArgsConstructor
public class GiftController {
    // 注入 Service
    // private final GiftService service;
}
