package com.campus.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 后台数据看板 - 核心 KPI 概览指标与图表统计对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardOverviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 系统学生总人数
     */
    private Long totalStudents;

    /**
     * 累计打卡签到总人次
     */
    private Long totalCheckins;

    /**
     * 全校平均签到率（例如 "87.5%"）
     */
    private String checkinRate;

    /**
     * 当前全校可用流通积分总量
     */
    private Long circulatingPoints;

    /**
     * 历史累计发放总积分总量
     */
    private Long totalPoints;

    /**
     * 已发布活动总数
     */
    private Long totalActivities;

    /**
     * 活动分类分布统计（供饼图/环形图展示）
     */
    private List<CategoryStatVO> categoryDistribution;

    /**
     * 近 7 天打卡趋势统计（供面积折线图展示）
     */
    private List<DailyTrendVO> weeklyCheckinTrend;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryStatVO implements Serializable {
        private String category;
        private String name;
        private Long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyTrendVO implements Serializable {
        private String date;
        private Long count;
    }
}
