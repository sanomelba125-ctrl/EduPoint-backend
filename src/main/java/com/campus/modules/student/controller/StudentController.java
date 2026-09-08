package com.campus.modules.student.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

/**
 * Student 控制器
 * 负责处理 HTTP 请求与响应
 */
@RestController
@RequestMapping("/api/student/")
@RequiredArgsConstructor
public class StudentController {
    // 注入 Service
    // private final StudentService service;
}
