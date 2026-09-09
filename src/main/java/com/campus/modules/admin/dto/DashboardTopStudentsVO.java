package com.campus.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 后台数据看板 - 积分 Top 5 榜单与最新实时打卡流水展示对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardTopStudentsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 全校积分榜 Top 5 学生
     */
    private List<TopStudentVO> topStudents;

    /**
     * 最新打卡实时流水
     */
    private List<RecentCheckinVO> recentCheckins;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopStudentVO implements Serializable {
        private Integer rank;
        private Long id;
        private String studentNo;
        private String name;
        private String className;
        private Integer points;        // 累计总积分 total_points
        private Integer currentPoints; // 可用积分 current_points
        private String avatar;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentCheckinVO implements Serializable {
        private Long id;
        private String studentNo;
        private String name;
        private String className;
        private String activity; // 活动标题
        private Integer points;  // 获得奖励积分
        private String time;     // 格式化时间，如 "10:24:15"
        private Integer status;  // 打卡状态: 1-已完成
    }
}
