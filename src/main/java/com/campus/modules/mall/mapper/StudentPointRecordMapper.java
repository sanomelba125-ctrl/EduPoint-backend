package com.campus.modules.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.modules.mall.entity.StudentPointRecord;
import org.apache.ibatis.annotations.Mapper;

import com.campus.modules.mall.dto.LedgerMetricsRow;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StudentPointRecordMapper extends BaseMapper<StudentPointRecord> {

    /**
     * 单条 SQL 聚合全库流水指标：
     * - earned: 活动打卡奖励(+)、退单返还(+)、正向人工调账(+)
     * - spent: 商城兑换消费(-)、负向人工调账(-)
     * - refunded: 累计退单返还(+)
     * - adjustCount: 人工调账总笔数
     */
    @Select("SELECT " +
            "  COALESCE(SUM(CASE WHEN type = 1 THEN GREATEST(change_points, 0) " +
            "                    WHEN type = 4 THEN GREATEST(change_points, 0) " +
            "                    WHEN type = 3 AND change_points > 0 THEN change_points " +
            "                    ELSE 0 END), 0) AS earned, " +
            "  COALESCE(SUM(CASE WHEN type = 2 THEN ABS(change_points) " +
            "                    WHEN type = 3 AND change_points < 0 THEN ABS(change_points) " +
            "                    ELSE 0 END), 0) AS spent, " +
            "  COALESCE(SUM(CASE WHEN type = 4 THEN GREATEST(change_points, 0) ELSE 0 END), 0) AS refunded, " +
            "  COALESCE(SUM(CASE WHEN type = 3 THEN 1 ELSE 0 END), 0) AS adjustCount " +
            "FROM student_point_record WHERE deleted = 0")
    LedgerMetricsRow selectLedgerMetrics();
}
