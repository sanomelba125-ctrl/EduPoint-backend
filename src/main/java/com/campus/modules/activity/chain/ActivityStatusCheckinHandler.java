package com.campus.modules.activity.chain;

import com.campus.common.exception.BusinessException;
import com.campus.common.result.ResultCode;
import com.campus.modules.activity.entity.Activity;
import com.campus.modules.activity.mapper.ActivityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 责任链节点 1：活动状态与时间窗口核验
 */
@Component
@RequiredArgsConstructor
public class ActivityStatusCheckinHandler extends AbstractCheckinHandler {

    private final ActivityMapper activityMapper;

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    protected void doHandle(CheckinContext context) {
        Long activityId = context.getDto().getActivityId();
        Activity activity = context.getActivity();
        if (activity == null) {
            activity = activityMapper.selectById(activityId);
            context.setActivity(activity);
        }
        if (activity == null || Objects.equals(activity.getDeleted(), 1) || Objects.equals(activity.getStatus(), 3)) {
            throw new BusinessException(ResultCode.ACTIVITY_EXPIRED.getCode(), "活动不存在或已关闭");
        }

        boolean isOngoing = Objects.equals(activity.getStatus(), 1);
        // 实时时间窗口补偿：若活动已进入有效开场时间但定时任务尚未轮询到，允许学生即时扫码打卡
        if (!isOngoing && activity.getStartTime() != null && activity.getEndTime() != null) {
            LocalDateTime now = LocalDateTime.now();
            if (!now.isBefore(activity.getStartTime()) && !now.isAfter(activity.getEndTime())) {
                isOngoing = true;
            }
        }
        if (!isOngoing) {
            throw new BusinessException(ResultCode.ACTIVITY_EXPIRED.getCode(), "活动未开始或已结束");
        }
    }
}
