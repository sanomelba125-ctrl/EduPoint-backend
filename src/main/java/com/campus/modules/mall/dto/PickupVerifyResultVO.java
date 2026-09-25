package com.campus.modules.mall.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 扫码枪秒级核销成功结果响应 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PickupVerifyResultVO {

    /** 订单ID */
    private Long orderId;

    /** 订单号 */
    private String orderNo;

    /** 所属学生ID */
    private Long studentId;

    /** 兑换礼品名称 */
    private String giftName;

    /** 兑换数量 */
    private Integer quantity;

    /** 消耗积分总额 */
    private Integer pointsCost;

    /** 提货核销时间 */
    private LocalDateTime deliveryTime;

    /** 订单当前状态 (2-已完成/已提货) */
    private Integer status;
}
