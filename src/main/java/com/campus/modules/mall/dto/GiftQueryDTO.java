package com.campus.modules.mall.dto;

import lombok.Data;

/**
 * 礼品分页检索入参
 */
@Data
public class GiftQueryDTO {

    private Integer page = 1;

    private Integer size = 10;

    private String keyword;

    private Integer status;
}
