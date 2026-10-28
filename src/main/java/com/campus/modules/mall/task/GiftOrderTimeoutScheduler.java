package com.campus.modules.mall.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.modules.mall.entity.StudentGift;
import com.campus.modules.mall.enums.OrderStatus;
import com.campus.modules.mall.service.StudentGiftService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时未提货订单的兜底关单任务。
 *
 * <p><b>为什么需要它：</b>正常关单依赖「延时队列 + 死信」：下单时投一条 TTL 24 小时的消息，
 * 到期后转死信队列触发 {@code expireOrder}。但这条链路上任何一环失效都会让订单永久停在
 * 「待提货」—— 消息投递失败、Broker 重启丢消息、队列被误删、消费端长期停机。
 * 结果是学生积分长期被占用、库存长期被占着不放，而且没有任何告警。
 *
 * <p>本任务以 MySQL 为唯一事实来源，定时扫描「创建时间超过关单时效 + 容差」仍为待提货的订单，
 * 直接走与死信消费完全相同的 {@code expireOrder} 路径（状态守卫 + 退分 + 回补库存），
 * 因此两者并发时也只会有一方生效，天然幂等。
 *
 * <p>容差 5 分钟：避免与 MQ 主链路在同一时刻抢同一批订单，让正常链路先跑完。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "campus.schedule.enabled", havingValue = "true", matchIfMissing = true)
public class GiftOrderTimeoutScheduler {

    /** 与 MQ 主链路的错峰容差：主链路优先，本任务只在明显超期后介入。 */
    private static final Duration TOLERANCE = Duration.ofMinutes(5);

    /** 单轮处理上限，避免一次拉起过多订单把数据库与事务日志打满。 */
    private static final int BATCH_LIMIT = 100;

    private final StudentGiftService studentGiftService;

    /** 关单时效，与延时队列 TTL 同源配置，避免两边口径不一致。 */
    @Value("${campus.mall.order-timeout-ms:86400000}")
    private long orderTimeoutMs;

    @Scheduled(fixedDelay = 600_000, initialDelay = 60_000)
    public void closeExpiredPendingOrders() {
        LocalDateTime deadline = LocalDateTime.now()
                .minus(Duration.ofMillis(orderTimeoutMs))
                .minus(TOLERANCE);

        List<StudentGift> expiredOrders;
        try {
            expiredOrders = studentGiftService.list(new LambdaQueryWrapper<StudentGift>()
                    .eq(StudentGift::getStatus, OrderStatus.PENDING.getCode())
                    .and(w -> w.isNull(StudentGift::getDeleted).or().eq(StudentGift::getDeleted, 0))
                    .le(StudentGift::getCreateTime, deadline)
                    .orderByAsc(StudentGift::getCreateTime)
                    .last("LIMIT " + BATCH_LIMIT));
        } catch (Exception e) {
            log.warn("[超时兜底关单] 扫描超时订单失败，等待下一轮: {}", e.getMessage());
            return;
        }

        if (expiredOrders == null || expiredOrders.isEmpty()) {
            return;
        }

        log.warn("[超时兜底关单] 扫描到 {} 笔超过 {} 小时仍未提货的订单，开始兜底关单（正常链路应已处理，出现即说明 MQ 延时消息可能丢失）",
                expiredOrders.size(), orderTimeoutMs / 3_600_000L);

        int closed = 0;
        int failed = 0;
        for (StudentGift order : expiredOrders) {
            try {
                studentGiftService.expireOrder(order.getId());
                closed++;
            } catch (Exception e) {
                failed++;
                log.error("[超时兜底关单] 单笔关单失败，不影响其余订单: orderId={}, orderNo={}, err={}",
                        order.getId(), order.getOrderNo(), e.getMessage());
            }
        }
        log.warn("[超时兜底关单] 本轮处理结束: 尝试 {} 笔，成功 {} 笔，异常 {} 笔", expiredOrders.size(), closed, failed);
    }
}
