package com.campus.modules.mall.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 礼品展示视图对象
 */
@Data
public class GiftVO {

    private Long id;

    private String name;

    private String description;

    private String image;

    private Integer pointsRequired;

    private Integer totalStock;

    private Integer stock;

    private Integer version;

    private Integer status;

    private LocalDateTime createTime;

    /**
     * 兼容前端 pointsCost 命名
     */
    public Integer getPointsCost() {
        return pointsRequired;
    }
}
