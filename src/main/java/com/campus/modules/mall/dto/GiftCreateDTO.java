package com.campus.modules.mall.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 礼品创建请求入参
 */
@Data
public class GiftCreateDTO {

    @NotBlank(message = "礼品名称不能为空")
    private String name;

    private String description;

    private String image;

    @NotNull(message = "兑换所需积分不能为空")
    @Min(value = 1, message = "所需积分必须大于0")
    private Integer pointsRequired;

    @NotNull(message = "入库初始库存不能为空")
    @Min(value = 0, message = "初始库存不能为负数")
    private Integer totalStock;

    private Integer status = 1;
}
