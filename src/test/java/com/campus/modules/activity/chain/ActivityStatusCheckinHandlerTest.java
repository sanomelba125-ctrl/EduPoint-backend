package com.campus.modules.activity.chain;

import com.campus.common.exception.BusinessException;
import com.campus.modules.activity.dto.ActivityCheckinDTO;
import com.campus.modules.activity.entity.Activity;
import com.campus.modules.activity.enums.ActivityStatus;
import com.campus.modules.activity.mapper.ActivityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * P1：活动时间窗口必须是签到硬约束，不能被滞后的 status 绕过。
 */
class ActivityStatusCheckinHandlerTest {

    private ActivityStatusCheckinHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ActivityStatusCheckinHandler(mock(ActivityMapper.class));
    }

    private Activity activity(ActivityStatus status, LocalDateTime start, LocalDateTime end) {
        Activity activity = new Activity();
        activity.setId(1L);
        activity.setDeleted(0);
        activity.setStatus(status.getCode());
        activity.setStartTime(start);
        activity.setEndTime(end);
        return activity;
    }

    private CheckinContext context(Activity activity) {
        ActivityCheckinDTO dto = new ActivityCheckinDTO();
        dto.setActivityId(1L);
        dto.setNonce("nonce");
        dto.setSign("sign");
        return CheckinContext.builder()
                .studentId(1L)
                .dto(dto)
                .activity(activity)
                .build();
    }

    private BusinessException assertRejected(Activity activity) {
        return assertThrows(BusinessException.class, () -> handler.handle(context(activity)));
    }

    @Test
    @DisplayName("状态仍是 IN_PROGRESS，但已过 endTime：必须拒绝（定时任务延迟不能放行）")
    void inProgressButEndedMustReject() {
        LocalDateTime now = LocalDateTime.now();
        BusinessException ex = assertRejected(
                activity(ActivityStatus.IN_PROGRESS, now.minusHours(2), now.minusMinutes(1)));

        assertTrue(ex.getMessage().contains("活动已结束"), "实际消息: " + ex.getMessage());
    }

    @Test
    @DisplayName("状态 IN_PROGRESS 且当前时间在窗口内：放行")
    void inProgressWithinWindowMustPass() {
        LocalDateTime now = LocalDateTime.now();
        assertDoesNotThrow(() -> handler.handle(
                context(activity(ActivityStatus.IN_PROGRESS, now.minusHours(1), now.plusHours(1)))));
    }

    @Test
    @DisplayName("状态仍是 NOT_STARTED，但时间已进入窗口：放行（定时任务尚未推进状态）")
    void notStartedButWindowOpenMustPass() {
        LocalDateTime now = LocalDateTime.now();
        assertDoesNotThrow(() -> handler.handle(
                context(activity(ActivityStatus.NOT_STARTED, now.minusMinutes(5), now.plusHours(1)))));
    }

    @Test
    @DisplayName("状态 NOT_STARTED 且尚未到 startTime：拒绝")
    void notStartedBeforeStartMustReject() {
        LocalDateTime now = LocalDateTime.now();
        BusinessException ex = assertRejected(
                activity(ActivityStatus.NOT_STARTED, now.plusHours(1), now.plusHours(2)));

        assertTrue(ex.getMessage().contains("活动尚未开始"), "实际消息: " + ex.getMessage());
    }

    @Test
    @DisplayName("状态 ENDED 即使时间字段看起来还在窗口内：拒绝（显式终态优先）")
    void endedMustRejectEvenWithinTimeWindow() {
        LocalDateTime now = LocalDateTime.now();
        assertRejected(activity(ActivityStatus.ENDED, now.minusHours(1), now.plusHours(1)));
    }

    @Test
    @DisplayName("状态 CLOSED：拒绝")
    void closedMustReject() {
        LocalDateTime now = LocalDateTime.now();
        BusinessException ex = assertRejected(
                activity(ActivityStatus.CLOSED, now.minusHours(1), now.plusHours(1)));

        assertTrue(ex.getMessage().contains("活动不存在或已关闭"), "实际消息: " + ex.getMessage());
    }

    @Test
    @DisplayName("活动已逻辑删除：拒绝")
    void deletedMustReject() {
        LocalDateTime now = LocalDateTime.now();
        Activity activity = activity(ActivityStatus.IN_PROGRESS, now.minusHours(1), now.plusHours(1));
        activity.setDeleted(1);

        assertRejected(activity);
    }

    @Test
    @DisplayName("状态 NOT_STARTED 且未配置时间窗口：仍按状态拒绝")
    void notStartedWithoutTimeWindowMustReject() {
        assertRejected(activity(ActivityStatus.NOT_STARTED, null, null));
    }

    @Test
    @DisplayName("状态 IN_PROGRESS 且未配置时间窗口：按状态放行")
    void inProgressWithoutTimeWindowMustPass() {
        assertDoesNotThrow(() -> handler.handle(context(activity(ActivityStatus.IN_PROGRESS, null, null))));
    }
}
