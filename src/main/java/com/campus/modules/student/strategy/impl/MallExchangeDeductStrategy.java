package com.campus.modules.student.strategy.impl;

import com.campus.common.exception.BusinessException;
import com.campus.modules.student.strategy.PointCalculationResult;
import com.campus.modules.student.strategy.PointContext;
import com.campus.modules.student.strategy.PointRewardStrategy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 策略实现：商城礼品兑换扣除积分 (type = 2)
 * 规则：扣减可用积分（必须防变负穿透），历史累计总积分保持不变（光荣榜排名不掉）
 */
@Component
public class MallExchangeDeductStrategy implements PointRewardStrategy {

    @Override
    public boolean supports(Integer type) {
        return type != null && type == 2;
    }

    @Override
    public PointCalculationResult calculate(PointContext context) {
        int cost = context.change() != null ? Math.abs(context.change()) : 0;
        if (cost <= 0) {
            throw new BusinessException("礼品兑换扣除分值必须大于 0");
        }
        int before = context.currentPoints() != null ? context.currentPoints() : 0;
        if (before < cost) {
            throw new BusinessException(String.format("可用积分不足！当前余额: %d 积分，本次兑换需要: %d 积分", before, cost));
        }
        int after = before - cost;
        int pointChange = -cost;

        String remark = StringUtils.hasText(context.reason())
                ? context.reason()
                : "商城礼品兑换扣除积分";

        String bizNo = StringUtils.hasText(context.bizNo())
                ? context.bizNo().trim()
                : "EXC-ORD-" + context.refId();

        return new PointCalculationResult(pointChange, before, after, false, remark, bizNo);
    }
}
