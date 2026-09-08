package com.campus.modules.certificate.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

/**
 * Certificate 控制器
 * 负责处理 HTTP 请求与响应
 */
@RestController
@RequestMapping("/api/certificate/")
@RequiredArgsConstructor
public class CertificateController {
    // 注入 Service
    // private final CertificateService service;
}
