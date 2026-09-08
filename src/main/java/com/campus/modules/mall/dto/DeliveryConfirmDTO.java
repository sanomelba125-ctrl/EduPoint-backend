package com.campus.modules.mall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 礼品核销确认入参
 */
@Data
public class DeliveryConfirmDTO {

    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @NotBlank(message = "提货核销码不能为空")
    private String pickupCode;

    private String remark;
}
