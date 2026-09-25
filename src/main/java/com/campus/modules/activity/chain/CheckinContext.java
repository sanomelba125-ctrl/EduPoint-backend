package com.campus.modules.activity.chain;

import com.campus.modules.activity.dto.ActivityCheckinDTO;
import com.campus.modules.activity.entity.Activity;
import com.campus.modules.activity.entity.ActivityCheckinCode;
import com.campus.modules.activity.entity.StudentActivity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 签到核销责任链上下文 (Chain of Responsibility Context)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckinContext {

    /** 学生ID */
    private Long studentId;

    /** 客户端提交的打卡请求入参 */
    private ActivityCheckinDTO dto;

    /** 当前打卡的活动实体 */
    private Activity activity;

    /** 学生的报名/选课记录 */
    private StudentActivity studentActivity;

    /** 当前扫码匹配到的服务端有效签到码记录 */
    private ActivityCheckinCode checkinCode;

    /** 实际测得的 GPS 打卡距离（米） */
    private Integer checkinDistance;

    /** 打卡判定生效时间 */
    private LocalDateTime checkinTime;
}
