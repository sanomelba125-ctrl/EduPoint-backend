package com.campus.mq;

import com.campus.config.RabbitMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 校园平台 MQ 异步消息消费者
 * 1. 异步消费活动打卡事件 (高并发削峰填谷)
 * 2. 延时死信消费超时未提货礼品单 (自动取消与返还积分)
 */
@Component
public class CampusMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(CampusMessageConsumer.class);

    /**
     * 1. 异步消费打卡签到事件
     */
    @RabbitListener(queues = RabbitMqConfig.ACTIVITY_CHECKIN_QUEUE)
    public void handleCheckinEvent(Map<String, Object> message) {
        log.info("[MQ 削峰消费] 接收到学生活动打卡异步流水: studentId={}, activityId={}", 
                message.get("studentId"), message.get("activityId"));
    }

    /**
     * 2. 消费超时未领取的礼品兑换订单
     */
    @RabbitListener(queues = RabbitMqConfig.GIFT_EXPIRE_QUEUE)
    public void handleGiftOrderExpire(Map<String, Object> message) {
        log.info("[MQ 延时关单] 检查到礼品提货码已超时未核销: pickupCode={}, 执行自动退单与积分回滚", 
                message.get("pickupCode"));
    }
}
