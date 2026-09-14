package com.campus.modules.activity.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.common.constant.UserRole;
import com.campus.common.result.Result;
import com.campus.modules.activity.dto.ActivityRecordMetricsVO;
import com.campus.modules.activity.dto.ActivityRecordVO;
import com.campus.modules.activity.service.StudentActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/activity/record")
@SaCheckRole(value = {UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.COUNSELOR}, mode = SaMode.OR)
@RequiredArgsConstructor
public class ActivityRecordController {
    private final StudentActivityService service;

    @GetMapping("/list")
    public Result<IPage<ActivityRecordVO>> list(@RequestParam(defaultValue="1") Integer page, @RequestParam(defaultValue="10") Integer size,
                                                 @RequestParam(required=false) String studentName, @RequestParam(required=false) String studentNo,
                                                 @RequestParam(required=false) String activityTitle, @RequestParam(required=false) Integer status) {
        return Result.success(service.adminList(page, size, studentName, studentNo, activityTitle, status));
    }

    @GetMapping({"/metrics", "/admin/metrics"})
    public Result<ActivityRecordMetricsVO> metrics() {
        return Result.success(service.getRecordMetrics());
    }
}
