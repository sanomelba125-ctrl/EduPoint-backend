package com.campus.mq;

import com.campus.config.RabbitMqConfig;
import com.campus.modules.activity.service.ActivityCacheService;
import com.campus.modules.mall.service.StudentGiftService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.UUID;

/**
 * 校园平台 MQ 异步消息消费者
 * 1. 异步消费活动打卡事件 (高并发削峰填谷)
 * 2. 延时死信消费超时未提货礼品单 (自动取消与返还积分)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CampusMessageConsumer {

    private final ActivityCacheService activityCacheService;
    private final StudentGiftService studentGiftService;

    @RabbitListener(queues = RabbitMqConfig.ACTIVITY_CACHE_REFRESH_QUEUE, autoStartup = "true")
    public void refreshActivityCache(Map<String, Object> message,
                                     @Header(value = "x-trace-id", required = false) String traceId) {
        bindTraceId(traceId, message);
        try {
            Object value = message.get("activityId");
            if (value instanceof Number number) {
                activityCacheService.refresh(number.longValue());
            } else if (value != null) {
                activityCacheService.refresh(Long.parseLong(value.toString()));
            }
        } finally {
            MDC.remove("traceId");
        }
    }

    /**
     * 1. 异步消费打卡签到事件
     */
    @RabbitListener(queues = RabbitMqConfig.ACTIVITY_CHECKIN_QUEUE, autoStartup = "${spring.rabbitmq.listener.simple.auto-startup:true}")
    public void handleCheckinEvent(Map<String, Object> message,
                                   @Header(value = "x-trace-id", required = false) String traceId) {
        bindTraceId(traceId, message);
        try {
            log.info("[MQ 削峰消费] 接收到学生活动打卡异步流水: studentId={}, activityId={}", 
                    message.get("studentId"), message.get("activityId"));
        } finally {
            MDC.remove("traceId");
        }
    }

    /**
     * 2. 消费超时未领取的礼品兑换订单 (延时死信自动关单)
     */
    @RabbitListener(queues = RabbitMqConfig.GIFT_EXPIRE_QUEUE, autoStartup = "${spring.rabbitmq.listener.simple.auto-startup:true}")
    public void handleGiftOrderExpire(Map<String, Object> message,
                                      @Header(value = "x-trace-id", required = false) String traceId) {
        bindTraceId(traceId, message);
        try {
            if (message == null) {
                return;
            }
            Object orderIdVal = message.get("orderId");
            Long orderId = null;
            if (orderIdVal instanceof Number number) {
                orderId = number.longValue();
            } else if (orderIdVal != null) {
                try {
                    orderId = Long.parseLong(orderIdVal.toString());
                } catch (NumberFormatException e) {
                    log.warn("[MQ 延时关单] 解析 orderId 格式异常: val={}, error={}", orderIdVal, e.getMessage());
                }
            }
            log.info("[MQ 延时关单] 检查到礼品提货码已超时未核销: orderId={}, pickupCode={}, 执行自动退单与积分回滚", 
                    orderId, message.get("pickupCode"));
            if (orderId != null) {
                try {
                    studentGiftService.expireOrder(orderId);
                } catch (Exception e) {
                    log.error("[MQ 延时关单] 自动关单执行失败: orderId={}", orderId, e);
                    throw new RuntimeException("礼品订单自动关单失败: orderId=" + orderId, e);
                }
            }
        } finally {
            MDC.remove("traceId");
        }
    }

    private void bindTraceId(String headerTraceId, Map<String, Object> message) {
        String traceId = headerTraceId;
        if (!StringUtils.hasText(traceId) && message != null) {
            Object msgTrace = message.get("x-trace-id");
            if (msgTrace == null) {
                msgTrace = message.get("traceId");
            }
            if (msgTrace != null) {
                traceId = msgTrace.toString();
            }
        }
        if (!StringUtils.hasText(traceId)) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put("traceId", traceId.trim());
    }
}
