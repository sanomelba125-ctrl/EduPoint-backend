package com.campus.controller;

import com.campus.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "00. 系统健康监控与测试", description = "用于前后端联调状态检查与连通性测试")
@RestController
@RequestMapping("/health")
public class HealthController {

    @Operation(summary = "系统运行状态探针")
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
