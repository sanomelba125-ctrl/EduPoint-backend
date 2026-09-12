package com.campus.modules.mall.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 礼品兑换订单全局聚合指标
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GiftOrderMetricsVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 全量兑换订单数 */
    private Long totalCount;

    /** 待现场提货订单数 (PENDING) */
    private Long pendingCount;

    /** 已核销出库订单数 (COMPLETED) */
    private Long completedCount;

    /** 已取消/退款订单数 (CANCELLED) */
    private Long cancelledCount;

    /** 全量有效兑换消耗积分总额 */
    private Long totalPoints;
}
