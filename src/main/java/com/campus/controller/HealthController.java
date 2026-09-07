package com.campus.controller;

import com.campus.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping
    public Result<Map<String, Object>> healthCheck() {
        Map<String, Object> status = new HashMap<>();
        status.put("service", "campus-activity-platform-backend");
        status.put("status", "UP");
        status.put("version", "1.0.0");
        status.put("systemTime", System.currentTimeMillis());
        return Result.success("后端服务正常运行中", status);
    }
}
