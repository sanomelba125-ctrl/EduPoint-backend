package com.campus.modules.mall.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 礼品兑换成功响应载体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GiftExchangeVO {

    /** 订单编号 */
    private String orderNo;

    /** 8位唯一核销提货码 */
    private String pickupCode;

    /** 消耗积分总量 */
    private Integer pointsCost;

    /** 兑换后剩余可用积分 */
    private Integer remainPoints;

    /** 兑换礼品名称 */
    private String giftName;

    /** 兑换件数 */
    private Integer quantity;
}
