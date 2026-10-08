package com.campus.modules.mall.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.modules.mall.entity.Gift;
import com.campus.modules.mall.mapper.GiftMapper;
import com.campus.modules.mall.service.GiftStockRedisService;
import com.campus.modules.mall.service.LocalMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 礼品库存 Redis 与 MySQL 差异定时对账巡检任务 (P1-5)
 * 周期性扫描全部上架商品，以「MySQL 实存 − 在途预扣」为唯一正确可用量核准修正 Redis 库存。
 * 在途预扣量取自事务性发件箱中尚未确认完成的秒杀订单——否则对账会把在途预扣一并抹掉，
 * 让 Redis 凭空多出库存被重复预扣，形成超卖窗口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "campus.schedule.enabled", havingValue = "true", matchIfMissing = true)
public class StockReconciliationScheduler {

    private final GiftMapper giftMapper;
    private final GiftStockRedisService giftStockRedisService;
    private final LocalMessageService localMessageService;

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

            // 先一次性取回「在途预扣」快照（按礼品聚合），避免对每个礼品反复查发件箱
            Map<Long, Integer> inflightByGift = localMessageService.countInflightOrderQtyByGift();

            for (Gift gift : activeGifts) {
                giftStockRedisService.reconcileStock(gift.getId(), inflightByGift.getOrDefault(gift.getId(), 0));
            }
        } catch (Exception e) {
            log.error("[库存对账] 定时对账任务异常: {}", e.getMessage(), e);
        }
    }
}
