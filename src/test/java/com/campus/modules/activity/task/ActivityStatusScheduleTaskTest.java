package com.campus.modules.activity.task;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.campus.modules.activity.entity.Activity;
import com.campus.modules.activity.mapper.ActivityMapper;
import com.campus.modules.activity.service.ActivityCacheService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("活动状态自动流转定时任务单元测试 (P2-6)")
class ActivityStatusScheduleTaskTest {

    private ActivityMapper activityMapper;
    private ActivityCacheService activityCacheService;
    private ActivityStatusScheduleTask scheduleTask;

    @BeforeAll
    static void initMybatisPlusMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setDbConfig(new GlobalConfig.DbConfig());
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Activity.class);
    }

    @BeforeEach
    void setUp() {
        activityMapper = mock(ActivityMapper.class);
        activityCacheService = mock(ActivityCacheService.class);
        scheduleTask = new ActivityStatusScheduleTask(activityMapper, activityCacheService);
    }

    @Test
    @DisplayName("定时流转：活动开场时从未开始(0)流转为进行中(1)，并触发缓存失效")
    void shouldTransitionToOngoingWhenStartTimeReached() {
        Activity activity = new Activity();
        activity.setId(101L);
        activity.setTitle("晨跑打卡活动");
        activity.setStatus(0);
        activity.setStartTime(LocalDateTime.now().minusMinutes(5));
        activity.setEndTime(LocalDateTime.now().plusHours(2));
        activity.setDeleted(0);

        // 第一次 selectList 查开场活动，返回该活动；第二次查超时活动，返回空
        when(activityMapper.selectList(any()))
                .thenReturn(List.of(activity))
                .thenReturn(Collections.emptyList());

        when(activityMapper.update(eq(null), ArgumentMatchers.<Wrapper<Activity>>any())).thenReturn(1);

        scheduleTask.executeActivityStatusTransition();

        verify(activityMapper, times(1)).update(eq(null), ArgumentMatchers.<Wrapper<Activity>>any());
        verify(activityCacheService, times(1)).evict(101L);
    }

    @Test
    @DisplayName("定时流转：活动超时后从进行中(1)流转为已结束(2)，并触发缓存失效")
    void shouldTransitionToEndedWhenEndTimePassed() {
        Activity activity = new Activity();
        activity.setId(102L);
        activity.setTitle("学术讲座活动");
        activity.setStatus(1);
        activity.setStartTime(LocalDateTime.now().minusHours(3));
        activity.setEndTime(LocalDateTime.now().minusMinutes(10));
        activity.setDeleted(0);

        // 第一次 selectList 返回空；第二次查超时活动返回该活动
        when(activityMapper.selectList(any()))
                .thenReturn(Collections.emptyList())
                .thenReturn(List.of(activity));

        when(activityMapper.update(eq(null), ArgumentMatchers.<Wrapper<Activity>>any())).thenReturn(1);

        scheduleTask.executeActivityStatusTransition();

        verify(activityMapper, times(1)).update(eq(null), ArgumentMatchers.<Wrapper<Activity>>any());
        verify(activityCacheService, times(1)).evict(102L);
    }

    @Test
    @DisplayName("定时流转：无待流转活动时无多余更新和缓存失效操作")
    void shouldDoNothingWhenNoActivitiesNeedTransition() {
        when(activityMapper.selectList(any())).thenReturn(Collections.emptyList());

        scheduleTask.executeActivityStatusTransition();

        verify(activityMapper, never()).update(any(), any());
        verify(activityCacheService, never()).evict(any());
    }
}
