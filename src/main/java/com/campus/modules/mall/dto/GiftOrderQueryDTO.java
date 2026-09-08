package com.campus.modules.mall.dto;

import lombok.Data;

/**
 * 礼品兑换订单分页查询参数
 */
@Data
public class GiftOrderQueryDTO {

    private Integer page = 1;

    private Integer size = 10;

    /** 订单编号 */
    private String orderNo;

    /** 提货核销码 */
    private String pickupCode;

    /** 学生姓名 */
    private String studentName;

    /** 学号 */
    private String studentNo;

    /** 订单状态: 1-待发放 2-已核销 3-已取消 */
    private Integer status;
}
