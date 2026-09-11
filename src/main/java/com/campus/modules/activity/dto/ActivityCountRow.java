package com.campus.modules.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 承接单个活动报名与打卡人数聚合统计 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityCountRow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 活动 ID */
    private Long activityId;

    /** 累计报名人次 */
    private Integer enrolledCount;

    /** 实际打卡出勤人次 */
    private Integer checkinCount;
}
