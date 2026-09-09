package com.campus.modules.admin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.campus.common.result.Result;
import com.campus.modules.admin.dto.DashboardOverviewVO;
import com.campus.modules.admin.dto.DashboardTopStudentsVO;
import com.campus.modules.admin.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台数据看板与统计大屏控制器
 * 支持 /dashboard 与 /admin/dashboard 双路径规范访问
 */
@Slf4j
@RestController
@RequestMapping({"/dashboard", "/admin/dashboard"})
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 获取数据看板核心指标概览 (学生总数、打卡总人次、签到率、流通积分、分类分布与趋势)
     */
    @SaCheckLogin
    @GetMapping("/overview")
    public Result<DashboardOverviewVO> getOverview() {
        return Result.success(dashboardService.getOverview());
    }

    /**
     * 获取数据看板学生积分光荣榜 Top 5 与近期实时打卡流水
     */
    @SaCheckLogin
    @GetMapping("/top-students")
    public Result<DashboardTopStudentsVO> getTopStudents() {
        return Result.success(dashboardService.getTopStudentsAndRecentCheckins());
    }
}
