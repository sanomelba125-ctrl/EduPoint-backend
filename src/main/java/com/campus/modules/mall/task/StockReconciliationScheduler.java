package com.campus.modules.mall.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.modules.mall.entity.Gift;
import com.campus.modules.mall.mapper.GiftMapper;
import com.campus.modules.mall.service.GiftStockRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 礼品库存 Redis 与 MySQL 差异定时对账巡检任务 (P1-5)
 * 周期性扫描全部上架商品，以 MySQL 数据库作为唯一事实来源核准修正 Redis 库存
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "campus.schedule.enabled", havingValue = "true", matchIfMissing = true)
public class StockReconciliationScheduler {

    private final GiftMapper giftMapper;
    private final GiftStockRedisService giftStockRedisService;

    @Scheduled(cron = "0 */5 * * * *")
    public void reconcileAllActiveGiftStocks() {
        try {
            List<Gift> activeGifts = giftMapper.selectList(
                    new LambdaQueryWrapper<Gift>()
                            .eq(Gift::getStatus, 1)
                            .eq(Gift::getDeleted, 0)
            );

            if (activeGifts == null || activeGifts.isEmpty()) {
                return;
            }

            for (Gift gift : activeGifts) {
                giftStockRedisService.reconcileStock(gift.getId());
            }
        } catch (Exception e) {
            log.error("[库存对账] 定时对账任务异常: {}", e.getMessage(), e);
        }
    }
}
