package com.campus.modules.activity.service;

import com.campus.config.RabbitMqConfig;
import com.campus.modules.activity.entity.Activity;
import com.campus.modules.activity.mapper.ActivityMapper;
import com.campus.modules.activity.service.impl.ActivityCacheServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 活动多级缓存（L1 Caffeine + L2 Redis + Pub/Sub 广播）单元测试
 */
class ActivityCacheServiceImplTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ActivityMapper activityMapper;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RTopic rTopic;

    private ObjectMapper objectMapper;
    private ActivityCacheServiceImpl cacheService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        when(redis.opsForValue()).thenReturn(valueOperations);
        when(redissonClient.getTopic(anyString())).thenReturn(rTopic);

        cacheService = new ActivityCacheServiceImpl(redis, objectMapper, activityMapper, rabbitTemplate, redissonClient);
        cacheService.initPubSub();
    }

    @Test
    @DisplayName("首次查询：L1/L2均未命中，回源查库并双写至 L1 Caffeine 与 L2 Redis")
    void getDetail_shouldQueryDbAndPopulateBothL1AndL2() {
        Long activityId = 100L;
        Activity dbActivity = new Activity();
        dbActivity.setId(activityId);
        dbActivity.setTitle("人工智能创新实训讲座");

        when(valueOperations.get("activity:detail:100")).thenReturn(null);
        when(activityMapper.selectById(activityId)).thenReturn(dbActivity);

        Activity result = cacheService.getDetail(activityId);

        assertNotNull(result);
        assertEquals("人工智能创新实训讲座", result.getTitle());
        verify(activityMapper, times(1)).selectById(activityId);
        verify(valueOperations, times(1)).set(eq("activity:detail:100"), anyString(), any(Duration.class));

        // 第二次查询：由于 L1 已缓存，直接从内存中返回，绝不穿透到 Redis 或 DB
        Activity cachedResult = cacheService.getDetail(activityId);
        assertSame(result, cachedResult);
        verify(activityMapper, times(1)).selectById(activityId); // 依然只调用了 1 次
        verify(valueOperations, times(1)).get(anyString()); // 依然只调用了 1 次
    }

    @Test
    @DisplayName("L2命中：L1未命中但L2 Redis命中时，回填L1并返回")
    void getDetail_shouldHitL2AndBackfillL1() throws Exception {
        Long activityId = 200L;
        Activity activity = new Activity();
        activity.setId(activityId);
        activity.setTitle("全国大学生创客大赛");

        ActivityCacheServiceImpl.CachedActivity cached = new ActivityCacheServiceImpl.CachedActivity();
        cached.activity = activity;
        cached.logicalExpireAt = LocalDateTime.now().plusMinutes(10);
        String json = objectMapper.writeValueAsString(cached);

        when(valueOperations.get("activity:detail:200")).thenReturn(json);

        Activity result = cacheService.getDetail(activityId);

        assertNotNull(result);
        assertEquals("全国大学生创客大赛", result.getTitle());
        verify(activityMapper, never()).selectById(any());

        // 验证已自动回填 L1
        Activity l1Result = cacheService.getDetail(activityId);
        assertEquals("全国大学生创客大赛", l1Result.getTitle());
        verify(valueOperations, times(1)).get("activity:detail:200"); // 后续请求无需再查 Redis
    }

    @Test
    @DisplayName("缓存驱逐：调用 evict 同时清空本地 L1、删除 L2 Redis 并广播 Pub/Sub 消息")
    void evict_shouldInvalidateL1DeleteL2AndPublishMessage() {
        Long activityId = 300L;
        Activity activity = new Activity();
        activity.setId(activityId);
        when(activityMapper.selectById(activityId)).thenReturn(activity);

        // 先预热进缓存
        cacheService.getDetail(activityId);

        // 执行驱逐
        cacheService.evict(activityId);

        // 验证 Redis 删除
        verify(redis, times(1)).delete("activity:detail:300");
        // 验证 Redisson 广播
        verify(rTopic, times(1)).publish(contains(":300"));

        // 验证 L1 已被清除（下次调用必须重新穿透）
        when(valueOperations.get("activity:detail:300")).thenReturn(null);
        cacheService.getDetail(activityId);
        verify(activityMapper, times(2)).selectById(activityId);
    }

    @Test
    @DisplayName("L2逻辑过期：命中但逻辑已过期时，返回旧数据同时触发异步刷新任务")
    void getDetail_shouldTriggerAsyncRefreshWhenLogicalExpired() throws Exception {
        Long activityId = 400L;
        Activity activity = new Activity();
        activity.setId(activityId);
        activity.setTitle("志愿服务经验交流会");

        ActivityCacheServiceImpl.CachedActivity cached = new ActivityCacheServiceImpl.CachedActivity();
        cached.activity = activity;
        cached.logicalExpireAt = LocalDateTime.now().minusMinutes(1); // 逻辑已过期
        String json = objectMapper.writeValueAsString(cached);

        when(valueOperations.get("activity:detail:400")).thenReturn(json);
        when(valueOperations.setIfAbsent(eq("activity:detail:400:rebuild-lock"), eq("1"), any(Duration.class)))
                .thenReturn(true);

        Activity result = cacheService.getDetail(activityId);

        assertNotNull(result);
        assertEquals("志愿服务经验交流会", result.getTitle());
        // 验证触发了 RabbitMQ 消息发送
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMqConfig.ACTIVITY_EXCHANGE),
                eq(RabbitMqConfig.ACTIVITY_CACHE_REFRESH_ROUTING_KEY),
                anyMap()
        );
    }

    @Test
    @DisplayName("可观测性：L1 本地缓存统计数据正常暴露")
    void getL1CacheStats_shouldReturnMetrics() {
        assertNotNull(cacheService.getL1CacheStats());
        assertTrue(cacheService.getL1CacheStats().requestCount() >= 0);
    }
}
