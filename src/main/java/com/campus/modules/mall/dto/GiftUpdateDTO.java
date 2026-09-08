package com.campus.modules.mall.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 礼品更新请求入参
 */
@Data
public class GiftUpdateDTO {

    @NotNull(message = "礼品ID不能为空")
    private Long id;

    private String name;

    private String description;

    private String image;

    @Min(value = 1, message = "所需积分必须大于0")
    private Integer pointsRequired;

    @Min(value = 0, message = "总库存不能为负数")
    private Integer totalStock;

    @Min(value = 0, message = "可用库存不能为负数")
    private Integer stock;

    private Integer status;

    public void setPointsCost(Integer pointsCost) {
        if (this.pointsRequired == null) {
            this.pointsRequired = pointsCost;
        }
    }

    public Integer getPointsCost() {
        return this.pointsRequired;
    }
}
