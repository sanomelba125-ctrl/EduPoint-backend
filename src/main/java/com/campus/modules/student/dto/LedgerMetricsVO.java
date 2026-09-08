package com.campus.modules.student.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 积分对账流水大盘指标数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerMetricsVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 累计活动打卡发放积分 (+) */
    private Integer totalEarnedPoints;

    /** 累计商城兑换消耗积分 (-) */
    private Integer totalSpentPoints;

    /** 累计退单返还积分 (+) */
    private Integer totalRefundedPoints;

    /** 系统特殊人工调账笔数 */
    private Integer adjustCount;
}
