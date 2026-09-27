package com.campus.modules.activity.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.common.constant.UserRole;
import com.campus.common.result.Result;
import com.campus.modules.activity.dto.ActivityPageQuery;
import com.campus.modules.activity.dto.ActivitySaveDTO;
import com.campus.modules.activity.dto.ActivityVO;
import com.campus.modules.activity.dto.QrCodeVO;
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

    @SaCheckRole(value = {UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.COUNSELOR}, mode = SaMode.OR)
    @PostMapping("/create")
    public Result<ActivityVO> create(@Valid @RequestBody ActivitySaveDTO dto) {
        return Result.success("活动创建成功", ActivityVO.fromEntity(activityService.create(dto)));
    }

    @SaCheckRole(value = {UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.COUNSELOR}, mode = SaMode.OR)
    @PutMapping("/update")
    public Result<ActivityVO> update(@Valid @RequestBody ActivitySaveDTO dto) {
        return Result.success("活动修改成功", ActivityVO.fromEntity(activityService.update(dto)));
    }

    @SaCheckRole(value = {UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.COUNSELOR}, mode = SaMode.OR)
    @DeleteMapping("/{id}")
    public Result<Void> archive(@PathVariable Long id) {
        activityService.archive(id);
        return Result.success("活动已归档", null);
    }

    @GetMapping("/{id}")
    public Result<ActivityVO> detail(@PathVariable Long id) {
        return Result.success(ActivityVO.fromEntity(activityService.detail(id)));
    }

    @GetMapping("/page")
    public Result<IPage<ActivityVO>> page(ActivityPageQuery query) {
        IPage<Activity> page = activityService.page(query);
        return Result.success(page == null ? null : page.convert(ActivityVO::fromEntity));
    }

    /**
     * 2.6 管理端生成/刷新活动动态签到二维码
     * GET /activity/{id}/qrcode
     * 权限：管理员登录
     */
    @SaCheckRole(value = {UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.COUNSELOR}, mode = SaMode.OR)
    @GetMapping("/{id}/qrcode")
    public Result<QrCodeVO> getQrCode(@PathVariable Long id) {
        return Result.success("二维码生成成功", activityService.generateQrCode(id));
    }
}
