package com.campus.modules.mall.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 数据库单次聚合统计流水指标行 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerMetricsRow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 累计活动打卡与正向调账发放积分 (+) */
    private Long earned;

    /** 累计商城兑换与负向调账扣减积分 (-) */
    private Long spent;

    /** 累计退单返还积分 (+) */
    private Long refunded;

    /** 人工调账笔数 */
    private Long adjustCount;
}
