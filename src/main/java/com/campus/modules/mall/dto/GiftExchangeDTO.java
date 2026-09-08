package com.campus.modules.mall.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 礼品兑换请求入参
 */
@Data
public class GiftExchangeDTO {

    @NotNull(message = "礼品ID不能为空")
    private Long giftId;

    @NotNull(message = "兑换件数不能为空")
    @Min(value = 1, message = "兑换数量至少为 1 件")
    @Max(value = 5, message = "单次兑换上限为 5 件")
    private Integer quantity = 1;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;
}
