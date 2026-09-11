package com.campus.modules.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.modules.mall.entity.StudentGift;
import org.apache.ibatis.annotations.Mapper;

import org.apache.ibatis.annotations.Select;

@Mapper
public interface StudentGiftMapper extends BaseMapper<StudentGift> {

    /**
     * 全局礼品兑换订单指标统计（SQL下推聚合，避免分页前端口径失真）
     */
    @Select("SELECT " +
            "  COUNT(*) AS totalCount, " +
            "  COALESCE(SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END), 0) AS pendingCount, " +
            "  COALESCE(SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END), 0) AS completedCount, " +
            "  COALESCE(SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END), 0) AS cancelledCount, " +
            "  COALESCE(SUM(CASE WHEN status != 3 THEN points_cost ELSE 0 END), 0) AS totalPoints " +
            "FROM student_gift WHERE deleted = 0")
    com.campus.modules.mall.dto.GiftOrderMetricsVO selectOrderMetrics();
}
