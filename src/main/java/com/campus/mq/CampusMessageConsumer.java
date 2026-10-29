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
 *
 * <h3>ACK 策略（2026-10-29 修复）</h3>
 * 全项目按「消息价值」分两档：
 * <ul>
 *   <li><b>资金链路（秒杀落库，{@link OrderCreateConsumer}）</b>：{@code ackMode = MANUAL}，
 *       由业务代码在订单事务提交后显式 {@code basicAck}，失败走 {@code basicNack(requeue=false)} 进死信队列。
 *       宁可人工核查，也不能丢单或重复回补库存。</li>
 *   <li><b>旁路副作用（本类三个队列）</b>：{@code ackMode = AUTO}，方法正常返回即确认。
 *       这三类消息都是幂等副作用：打卡事件只写审计日志，活动缓存刷新下一次读写会自愈，
 *       超时关单还有 {@code GiftOrderTimeoutScheduler} 每 10 分钟扫描兜底。
 *       因此失败时选择「记录 ERROR + 放弃重投」，而不是抛异常让 Broker 无限重投。</li>
 * </ul>
 *
 * <p><b>为什么必须显式声明 {@code ackMode}：</b>修复前全局配置是
 * {@code spring.rabbitmq.listener.simple.acknowledge-mode: manual}，而本类三个方法既不接收
 * {@code Channel} 也从未调用 {@code basicAck}，消息被投递后永远停在 Unacked。
 * 每个队列的未确认上限 = {@code prefetch × concurrency}（当时 10 × 4 = 40），
 * 攒满 40 条后该队列彻底停止投递，24h 自动关单链路直接假死；服务重启后未确认消息全部重投，
 * 整批历史消息被重复消费。实测 60 条消息只消费 40 条、剩余 20 条永久停在 ready。
 * 现改为「全局默认 auto + 需要手动的监听器显式声明 MANUAL」，
 * 后续新增监听器即使漏配，最坏是重复投递而不是静默堆积。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CampusMessageConsumer {

    private final ActivityCacheService activityCacheService;
    private final StudentGiftService studentGiftService;

    @RabbitListener(queues = RabbitMqConfig.ACTIVITY_CACHE_REFRESH_QUEUE,
            ackMode = "AUTO",
            autoStartup = "${spring.rabbitmq.listener.simple.auto-startup:true}")
    public void refreshActivityCache(Map<String, Object> message,
                                     @Header(value = "x-trace-id", required = false) String traceId) {
        bindTraceId(traceId, message);
        try {
            if (message == null) {
                log.warn("[MQ 活动缓存] 收到空消息体，跳过刷新");
                return;
            }
            Object value = message.get("activityId");
            if (value instanceof Number number) {
                activityCacheService.refresh(number.longValue());
            } else if (value != null) {
                activityCacheService.refresh(Long.parseLong(value.toString()));
            }
        } catch (Exception e) {
            // 缓存刷新是幂等副作用：不重投。下一次活动写操作会再次刷新，
            // 读路径也有 L1/L2 miss 回源，最坏只是多一次回源查询。
            log.warn("[MQ 活动缓存] 刷新活动缓存失败，放弃本条（读路径会回源自愈）: payload={}, err={}",
                    safePayload(message), e.getMessage(), e);
        } finally {
            MDC.remove("traceId");
        }
    }

    /**
     * 1. 异步消费打卡签到事件
     */
    @RabbitListener(queues = RabbitMqConfig.ACTIVITY_CHECKIN_QUEUE,
            ackMode = "AUTO",
            autoStartup = "${spring.rabbitmq.listener.simple.auto-startup:true}")
    public void handleCheckinEvent(Map<String, Object> message,
                                   @Header(value = "x-trace-id", required = false) String traceId) {
        bindTraceId(traceId, message);
        try {
            if (message == null) {
                log.warn("[MQ 削峰消费] 收到空消息体，跳过打卡流水");
                return;
            }
            log.info("[MQ 削峰消费] 接收到学生活动打卡异步流水: studentId={}, activityId={}",
                    message.get("studentId"), message.get("activityId"));
        } catch (Exception e) {
            log.warn("[MQ 削峰消费] 处理打卡流水失败，放弃本条（打卡事实已由签到事务落库）: payload={}, err={}",
                    safePayload(message), e.getMessage(), e);
        } finally {
            MDC.remove("traceId");
        }
    }

    /**
     * 2. 消费超时未领取的礼品兑换订单 (延时死信自动关单)
     *
     * <p>失败不重投：订单的最终一致性由 {@code GiftOrderTimeoutScheduler} 每 10 分钟扫描
     * 「超过关单时效仍未提货」的订单并调用 {@code expireOrder} 保证。
     * 若这里改成抛异常让 Broker 重投，一条无法处理的毒消息会在 prefetch 窗口内被立即反复投递，
     * 把日志和 CPU 打成热循环，反而挤占正常消息。24 小时的关单延迟再多等 10 分钟没有业务影响。</p>
     */
    @RabbitListener(queues = RabbitMqConfig.GIFT_EXPIRE_QUEUE,
            ackMode = "AUTO",
            autoStartup = "${spring.rabbitmq.listener.simple.auto-startup:true}")
    public void handleGiftOrderExpire(Map<String, Object> message,
                                      @Header(value = "x-trace-id", required = false) String traceId) {
        bindTraceId(traceId, message);
        try {
            if (message == null) {
                log.warn("[MQ 延时关单] 收到空消息体，跳过自动关单");
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
                    log.warn("[MQ 延时关单] 解析 orderId 格式异常，本条作废: val={}, error={}", orderIdVal, e.getMessage());
                    return;
                }
            }
            log.info("[MQ 延时关单] 检查到礼品提货码已超时未核销: orderId={}, pickupCode={}, 执行自动退单与积分回滚",
                    orderId, message.get("pickupCode"));
            if (orderId != null) {
                try {
                    studentGiftService.expireOrder(orderId);
                } catch (Exception e) {
                    log.error("[MQ 延时关单] 自动关单执行失败，本条不重投，交由 GiftOrderTimeoutScheduler 兜底: orderId={}",
                            orderId, e);
                }
            }
        } catch (Exception e) {
            // 兜底：连消息体解析都失败（毒消息）。直接确认丢弃，避免无限重投。
            log.error("[MQ 延时关单] 消息无法处理，已确认丢弃以避免热循环，等待定时任务兜底: payload={}", safePayload(message), e);
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

    /**
     * 日志里只保留消息的键集合与大小，避免把提货码、学生 ID 等业务明细整段写盘。
     */
    private String safePayload(Map<String, Object> message) {
        if (message == null) {
            return "null";
        }
        return "keys=" + message.keySet() + ", size=" + message.size();
    }
}
