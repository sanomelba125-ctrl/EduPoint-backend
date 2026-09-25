package com.campus.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务调度配置
 * 启用 Spring @Scheduled 注解支持，用于活动状态周期性自动流转、延时任务扫描等
 */
@Configuration
@EnableScheduling
public class ScheduleConfig {
}
