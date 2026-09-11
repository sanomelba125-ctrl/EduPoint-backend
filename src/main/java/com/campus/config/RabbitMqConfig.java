package com.campus.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 消息队列核心拓扑配置
 * 1. 校园活动打卡高并发削峰填谷队列（Direct）
 * 2. 礼品兑换超时未提货自动关单与积分回退延时死信队列 (TTL + DLX)
 */
@Configuration
public class RabbitMqConfig {

    // ================== 1. 活动打卡并发削峰队列 ==================
    public static final String ACTIVITY_EXCHANGE = "campus.activity.exchange";
    public static final String ACTIVITY_CHECKIN_QUEUE = "campus.activity.checkin.queue";
    public static final String ACTIVITY_CHECKIN_ROUTING_KEY = "activity.checkin";
    public static final String ACTIVITY_CACHE_REFRESH_QUEUE = "campus.activity.cache.refresh.queue";
    public static final String ACTIVITY_CACHE_REFRESH_ROUTING_KEY = "activity.cache.refresh";

    // ================== 2. 礼品兑换 10 分钟延时超时关单队列（DLX 架构） ==================
    public static final String GIFT_ORDER_EXCHANGE = "campus.gift.order.exchange";
    public static final String GIFT_ORDER_DELAY_QUEUE = "campus.gift.order.delay.queue";
    public static final String GIFT_ORDER_DELAY_ROUTING_KEY = "gift.order.delay";

    // 死信交换机与死信队列（接收超时未领取的兑换单并执行退单返还积分）
    public static final String GIFT_DLX_EXCHANGE = "campus.gift.dlx.exchange";
    public static final String GIFT_EXPIRE_QUEUE = "campus.gift.expire.queue";
    public static final String GIFT_EXPIRE_ROUTING_KEY = "gift.expire";

    /**
     * 使用 JSON 序列化器传输消息实体
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ----------------- 活动打卡削峰绑定 -----------------
    @Bean
    public DirectExchange activityExchange() {
        return new DirectExchange(ACTIVITY_EXCHANGE, true, false);
    }

    @Bean
    public Queue activityCheckinQueue() {
        return QueueBuilder.durable(ACTIVITY_CHECKIN_QUEUE).build();
    }

    @Bean
    public Binding activityCheckinBinding(Queue activityCheckinQueue, DirectExchange activityExchange) {
        return BindingBuilder.bind(activityCheckinQueue).to(activityExchange).with(ACTIVITY_CHECKIN_ROUTING_KEY);
    }

    @Bean
    public Queue activityCacheRefreshQueue() {
        return QueueBuilder.durable(ACTIVITY_CACHE_REFRESH_QUEUE).build();
    }

    @Bean
    public Binding activityCacheRefreshBinding(Queue activityCacheRefreshQueue, DirectExchange activityExchange) {
        return BindingBuilder.bind(activityCacheRefreshQueue).to(activityExchange).with(ACTIVITY_CACHE_REFRESH_ROUTING_KEY);
    }

    // ----------------- 礼品超时关单延时死信队列绑定 -----------------
    @Bean
    public DirectExchange giftOrderExchange() {
        return new DirectExchange(GIFT_ORDER_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange giftDlxExchange() {
        return new DirectExchange(GIFT_DLX_EXCHANGE, true, false);
    }

    /**
     * 延时队列：消息在此停留，到期后自动转发至死信交换机。
     */
    @Bean
    public Queue giftOrderDelayQueue() {
        Map<String, Object> args = new HashMap<>();
        // 绑定死信交换机和路由键。
        args.put("x-dead-letter-exchange", GIFT_DLX_EXCHANGE);
        // 绑定死信 Routing Key
        args.put("x-dead-letter-routing-key", GIFT_EXPIRE_ROUTING_KEY);
        // 默认延时 10 分钟。
        args.put("x-message-ttl", 600000); 
        return new Queue(GIFT_ORDER_DELAY_QUEUE, true, false, false, args);
    }

    @Bean
    public Binding giftOrderDelayBinding(Queue giftOrderDelayQueue, DirectExchange giftOrderExchange) {
        return BindingBuilder.bind(giftOrderDelayQueue).to(giftOrderExchange).with(GIFT_ORDER_DELAY_ROUTING_KEY);
    }

    /**
     * 死信消费队列：监听超时订单。
     */
    @Bean
    public Queue giftExpireQueue() {
        return QueueBuilder.durable(GIFT_EXPIRE_QUEUE).build();
    }

    @Bean
    public Binding giftExpireBinding(Queue giftExpireQueue, DirectExchange giftDlxExchange) {
        return BindingBuilder.bind(giftExpireQueue).to(giftDlxExchange).with(GIFT_EXPIRE_ROUTING_KEY);
    }
}
