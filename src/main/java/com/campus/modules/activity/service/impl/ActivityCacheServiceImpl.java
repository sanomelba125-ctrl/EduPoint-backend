package com.campus.modules.activity.service.impl;

import com.campus.config.RabbitMqConfig;
import com.campus.modules.activity.entity.Activity;
import com.campus.modules.activity.mapper.ActivityMapper;
import com.campus.modules.activity.service.ActivityCacheService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityCacheServiceImpl implements ActivityCacheService {
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final Duration REBUILD_LOCK_TTL = Duration.ofSeconds(30);
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ActivityMapper activityMapper;
    private final RabbitTemplate rabbitTemplate;

    @Override
    public Activity getDetail(Long activityId) {
        String key = key(activityId);
        try {
            String json = redis.opsForValue().get(key);
            if (json != null) {
                CachedActivity cached = objectMapper.readValue(json, CachedActivity.class);
                if (cached.activity != null) {
                    if (cached.logicalExpireAt == null || cached.logicalExpireAt.isBefore(LocalDateTime.now())) requestRefresh(activityId);
                    return cached.activity;
                }
            }
        } catch (Exception e) {
            log.warn("Activity cache read failed for {}", activityId, e);
        }
        Activity activity = activityMapper.selectById(activityId);
        if (activity != null) write(activity);
        return activity;
    }

    @Override
    public void refresh(Long activityId) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            evict(activityId);
            return;
        }
        write(activity);
    }

    @Override
    public void evict(Long activityId) {
        try { redis.delete(key(activityId)); } catch (Exception e) { log.warn("Activity cache eviction failed for {}", activityId, e); }
    }

    @Override
    public void requestRefresh(Long activityId) {
        String lockKey = key(activityId) + ":rebuild-lock";
        try {
            if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey, "1", REBUILD_LOCK_TTL))) {
                rabbitTemplate.convertAndSend(RabbitMqConfig.ACTIVITY_EXCHANGE,
                        RabbitMqConfig.ACTIVITY_CACHE_REFRESH_ROUTING_KEY, Map.of("activityId", activityId));
            }
        } catch (Exception e) {
            log.warn("Activity cache refresh request failed for {}", activityId, e);
        }
    }

    private void write(Activity activity) {
        try {
            CachedActivity cached = new CachedActivity();
            cached.activity = activity;
            cached.logicalExpireAt = LocalDateTime.now().plusMinutes(5);
            redis.opsForValue().set(key(activity.getId()), objectMapper.writeValueAsString(cached), CACHE_TTL);
        } catch (JsonProcessingException e) {
            log.warn("Activity cache serialization failed for {}", activity.getId(), e);
        }
    }

    private String key(Long activityId) { return "activity:detail:" + activityId; }

    public static class CachedActivity {
        public Activity activity;
        public LocalDateTime logicalExpireAt;
    }
}
