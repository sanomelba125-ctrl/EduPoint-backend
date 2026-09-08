package com.campus.modules.mall.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 订单取消与退还积分请求入参
 */
@Data
public class OrderCancelDTO {

    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    /** 是否原路退还积分 (默认退还) */
    private Boolean refundPoints = true;

    private String cancelReason;
}
