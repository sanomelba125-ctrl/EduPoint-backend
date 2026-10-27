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
        if (activity == null || Objects.equals(activity.getDeleted(), 1)
                || Objects.equals(activity.getStatus(), com.campus.modules.activity.enums.ActivityStatus.CLOSED.getCode())) {
            throw new BusinessException(ResultCode.ACTIVITY_EXPIRED.getCode(), "活动不存在或已关闭");
        }

        // 时间窗口是硬约束：活动状态由定时任务推进，存在延迟，不能因为 status 仍是 IN_PROGRESS
        // 就允许已经结束（或尚未开始）的活动签到。
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = activity.getStartTime();
        LocalDateTime endTime = activity.getEndTime();
        if (startTime != null && now.isBefore(startTime)) {
            throw new BusinessException(ResultCode.ACTIVITY_EXPIRED.getCode(), "活动尚未开始");
        }
        if (endTime != null && now.isAfter(endTime)) {
            throw new BusinessException(ResultCode.ACTIVITY_EXPIRED.getCode(), "活动已结束");
        }

        boolean isOngoing = Objects.equals(activity.getStatus(), com.campus.modules.activity.enums.ActivityStatus.IN_PROGRESS.getCode());
        // 只对「未开始」做时间窗口补偿（定时任务尚未推进状态）；
        // ENDED / CLOSED 是显式终态，即使时间字段看起来还在窗口内也必须拒绝。
        if (!isOngoing
                && Objects.equals(activity.getStatus(), com.campus.modules.activity.enums.ActivityStatus.NOT_STARTED.getCode())
                && startTime != null && endTime != null) {
            isOngoing = true;
        }
        if (!isOngoing) {
            throw new BusinessException(ResultCode.ACTIVITY_EXPIRED.getCode(), "活动未开始或已结束");
        }
    }
}
