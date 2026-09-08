package com.campus.modules.mall.dto;

import lombok.Data;
import org.springframework.util.StringUtils;

/**
 * 礼品分页检索入参
 */
@Data
public class GiftQueryDTO {

    private Integer page = 1;

    private Integer size = 10;

    private String keyword;

    private String name;

    private Integer status;

    public String getKeyword() {
        if (StringUtils.hasText(keyword)) {
            return keyword.trim();
        }
        if (StringUtils.hasText(name)) {
            return name.trim();
        }
        return null;
    }
}
