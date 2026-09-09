package com.campus.modules.activity.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.common.result.Result;
import com.campus.modules.activity.dto.*;
import com.campus.modules.activity.service.StudentActivityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 学生报名、候补及动态签到接口。 */
@RestController
@RequestMapping("/student/activity")
@RequiredArgsConstructor
public class StudentActivityController {
    private final StudentActivityService service;

    @SaCheckLogin @PostMapping("/signup")
    public Result<ActivitySignupVO> signup(@Valid @RequestBody ActivitySignupDTO dto) { return Result.success("报名成功", service.signup(dto)); }

    @SaCheckLogin @PostMapping("/cancel-signup")
    public Result<String> cancel(@Valid @RequestBody ActivitySignupDTO dto) {
        String message = service.cancelSignup(dto.getActivityId());
        return Result.success(message, message);
    }

    @SaCheckLogin @PostMapping("/checkin")
    public Result<ActivityCheckinVO> checkin(@Valid @RequestBody ActivityCheckinDTO dto) { return Result.success("打卡签到成功", service.checkin(dto)); }

    @SaCheckLogin @GetMapping("/my-list")
    public Result<IPage<MyActivityVO>> myList(@RequestParam(defaultValue="1") Integer page, @RequestParam(defaultValue="10") Integer size, @RequestParam(required=false) Integer status) { return Result.success(service.myList(page,size,status)); }

}
