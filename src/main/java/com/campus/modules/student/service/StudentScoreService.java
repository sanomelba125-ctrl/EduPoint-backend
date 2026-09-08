package com.campus.modules.student.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.modules.student.dto.*;

/**
 * 积分台账与学分光荣榜业务服务接口
 */
public interface StudentScoreService {

    /**
     * 查询学生当前可用积分与历史累计总积分（双积分模型）
     */
    StudentScoreBalanceVO getBalance(Long studentId);

    /**
     * 学生查询个人积分变动明细流水（支持按收入/支出筛选）
     */
    Page<StudentPointRecordVO> getMyPointRecords(Integer page, Integer size, Integer type, Long studentId);

    /**
     * 查询学分光荣榜（前 N 名列表 + 本人实时排名排位 + 距上一名分差）
     */
    StudentRankResultVO getLeaderboard(Integer page, Integer size, String college, Long currentStudentId);

    /**
     * 管理端人工积分调账（支持正向奖励与惩罚扣减，严格负数防穿透校验）
     */
    void adjustPoints(PointAdjustDTO dto, Long adminId);

    /**
     * 管理端全量财务对账流水检索
     */
    Page<StudentPointRecordVO> getAdminRecordPage(AdminPointRecordQueryDTO queryDTO);

    /**
     * 管理端对账流水大盘指标统计
     */
    LedgerMetricsVO getLedgerMetrics();
}
