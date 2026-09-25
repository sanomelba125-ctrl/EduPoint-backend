package com.campus.modules.student.strategy.impl;

import com.campus.common.exception.BusinessException;
import com.campus.modules.student.strategy.PointCalculationResult;
import com.campus.modules.student.strategy.PointContext;
import com.campus.modules.student.strategy.PointRewardStrategy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 策略实现：订单取消原路退还积分 (type = 4)
 * 规则：全额返还可用积分，历史累计总积分不重复增加
 */
@Component
public class OrderCancelRefundStrategy implements PointRewardStrategy {

    @Override
    public boolean supports(Integer type) {
        return type != null && type == 4;
    }

    @Override
    public PointCalculationResult calculate(PointContext context) {
        int refund = context.change() != null ? Math.abs(context.change()) : 0;
        if (refund <= 0) {
            throw new BusinessException("退款退还积分分值必须大于 0");
        }
        int before = context.currentPoints() != null ? context.currentPoints() : 0;
        int after = before + refund;

        String remark = StringUtils.hasText(context.reason()) ? context.reason() : "订单取消原路全额退还积分";

        String bizNo = StringUtils.hasText(context.bizNo())
                ? context.bizNo().trim()
                : "REF-" + context.refId();

        return new PointCalculationResult(refund, before, after, false, remark, bizNo);
    }
}
