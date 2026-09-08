package com.campus.modules.mall.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 礼品兑换订单展示视图对象
 */
@Data
public class StudentGiftVO {

    private Long id;

    private String orderNo;

    private Long studentId;

    private String studentNo;

    private String studentName;

    private String className;

    private Long giftId;

    private String giftName;

    private String giftImage;

    private Integer quantity;

    private Integer pointsCost;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    private String pickupCode;

    private Integer status;

    private Integer refundPointsStatus;

    private LocalDateTime deliveryTime;

    private LocalDateTime cancelTime;

    private String cancelReason;

    private Long operatorId;

    private String remark;

    private LocalDateTime createTime;
}
