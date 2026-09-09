package com.campus.modules.admin.service;

import com.campus.modules.admin.dto.DashboardOverviewVO;
import com.campus.modules.admin.dto.DashboardTopStudentsVO;

/**
 * 后台数据大屏与核心指标统计服务接口
 */
public interface DashboardService {

    /**
     * 获取数据看板核心指标概览与图表统计
     */
    DashboardOverviewVO getOverview();

    /**
     * 获取积分榜 Top 5 学生与最新打卡流水
     */
    DashboardTopStudentsVO getTopStudentsAndRecentCheckins();
}
