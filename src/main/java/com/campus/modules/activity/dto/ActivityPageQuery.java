package com.campus.modules.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 活动分页查询条件。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityPageQuery {

    private long page = 1;
    private long size = 10;
    private String title;
    private String category;
    private Integer status;
}
