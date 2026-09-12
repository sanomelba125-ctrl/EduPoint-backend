package com.campus.modules.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 学生活动打卡/参与记录全局聚合指标
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityRecordMetricsVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 全校活动参与总人次 */
    private Long totalRecords;

    /** 已出勤打卡人数 */
    private Long completedCount;

    /** 待打卡履约人次 */
    private Long pendingCount;

    /** 取消 / 未履约失效人次 */
    private Long cancelledCount;

    /** 累计已发放积分总额 */
    private Long pointsDistributed;
}
