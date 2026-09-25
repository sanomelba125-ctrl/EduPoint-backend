package com.campus.modules.mall.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 扫码枪/核销端扫码核验入参
 */
@Data
public class PickupQrVerifyDTO {

    /**
     * 扫码枪扫出的动态二维码 Token 内容
     */
    @NotBlank(message = "提货二维码内容不能为空")
    private String qrToken;

    /**
     * 核销工作人员备注
     */
    private String remark;
}
