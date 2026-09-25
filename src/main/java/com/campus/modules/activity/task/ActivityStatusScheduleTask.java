package com.campus.modules.activity.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.modules.activity.entity.Activity;
import com.campus.modules.activity.mapper.ActivityMapper;
import com.campus.modules.activity.service.ActivityCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 校园活动状态生命周期自动流转调度任务
 * 解决活动状态仅在创建/编辑时计算、活动开场/结束时状态未自动更新导致打卡失败与页面状态滞后的硬伤 (P2-6)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityStatusScheduleTask {

    private final ActivityMapper activityMapper;
    private final ActivityCacheService activityCacheService;

    /**
     * 每分钟整点执行一次，流转活动状态：
     * 1. 未开始(0) 且 当前时间在起止时间范围内 -> 自动流转为「进行中」(1)
     * 2. 未开始(0)或进行中(1) 且 当前时间已过结束时间 -> 自动流转为「已结束」(2)
     */
    @Scheduled(cron = "0 * * * * ?")
    public void executeActivityStatusTransition() {
        LocalDateTime now = LocalDateTime.now();

        // 1. 流转为「进行中」(0 -> 1)
        List<Activity> toStartList = activityMapper.selectList(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getStatus, 0)
                .le(Activity::getStartTime, now)
                .ge(Activity::getEndTime, now)
                .eq(Activity::getDeleted, 0));

        for (Activity activity : toStartList) {
            int rows = activityMapper.update(null, new LambdaUpdateWrapper<Activity>()
                    .set(Activity::getStatus, 1)
                    .eq(Activity::getId, activity.getId())
                    .eq(Activity::getStatus, 0));
            if (rows > 0) {
                log.info("[活动状态自动流转] 活动已到开场时间，状态变更为进行中: id={}, title={}",
                        activity.getId(), activity.getTitle());
                activityCacheService.evict(activity.getId());
            }
        }

        // 2. 流转为「已结束」(0, 1 -> 2)
        List<Activity> toEndList = activityMapper.selectList(new LambdaQueryWrapper<Activity>()
                .in(Activity::getStatus, List.of(0, 1))
                .lt(Activity::getEndTime, now)
                .eq(Activity::getDeleted, 0));

        for (Activity activity : toEndList) {
            int rows = activityMapper.update(null, new LambdaUpdateWrapper<Activity>()
                    .set(Activity::getStatus, 2)
                    .eq(Activity::getId, activity.getId())
                    .in(Activity::getStatus, List.of(0, 1)));
            if (rows > 0) {
                log.info("[活动状态自动流转] 活动已超过结束时间，状态变更为已结束: id={}, title={}",
                        activity.getId(), activity.getTitle());
                activityCacheService.evict(activity.getId());
            }
        }
    }
}
