package com.campus.modules.activity.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.common.result.Result;
import com.campus.modules.activity.dto.ActivityPageQuery;
import com.campus.modules.activity.dto.ActivitySaveDTO;
import com.campus.modules.activity.entity.Activity;
import com.campus.modules.activity.service.ActivityService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Activity 控制器
 * 负责处理 HTTP 请求与响应
 */
@RestController
@RequestMapping("/activity")
public class ActivityController {

    @Resource
    private ActivityService activityService;

    @PostMapping("/create")
    public Result<Activity> create(@Valid @RequestBody ActivitySaveDTO dto) {
        return Result.success("活动创建成功", activityService.create(dto));
    }

    @PutMapping("/update")
    public Result<Activity> update(@Valid @RequestBody ActivitySaveDTO dto) {
        return Result.success("活动修改成功", activityService.update(dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> archive(@PathVariable Long id) {
        activityService.archive(id);
        return Result.success("活动已归档", null);
    }

    @GetMapping("/{id}")
    public Result<Activity> detail(@PathVariable Long id) {
        return Result.success(activityService.detail(id));
    }

    @GetMapping("/page")
    public Result<IPage<Activity>> page(ActivityPageQuery query) {
        return Result.success(activityService.page(query));
    }
}
