package com.campus.modules.student.strategy.impl;

import com.campus.common.exception.BusinessException;
import com.campus.modules.student.strategy.PointCalculationResult;
import com.campus.modules.student.strategy.PointContext;
import com.campus.modules.student.strategy.PointRewardStrategy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * 策略实现：管理端人工审批调账 (type = 3)
 * 规则：正向增分同步增加累计积分；负向扣分仅扣可用且防穿透，不扣减历史累计
 */
@Component
public class AdminAdjustStrategy implements PointRewardStrategy {

    @Override
    public boolean supports(Integer type) {
        return type != null && type == 3;
    }

    @Override
    public PointCalculationResult calculate(PointContext context) {
        Integer change = context.change();
        if (change == null || change == 0) {
            throw new BusinessException("调账分值不能为 0");
        }
        int before = context.currentPoints() != null ? context.currentPoints() : 0;

        if (change < 0) {
            int deduct = Math.abs(change);
            if (before < deduct) {
                throw new BusinessException(String.format("扣减后可用积分将变负数，系统拦截操作！学生当前余额仅剩: %d 积分", before));
            }
        }

        int after = before + change;
        boolean addTotal = change > 0;

        String remark = StringUtils.hasText(context.reason()) ? context.reason() : "管理员人工调账";

        String bizNo = StringUtils.hasText(context.bizNo())
                ? context.bizNo().trim()
                : "ADJ-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        return new PointCalculationResult(change, before, after, addTotal, remark, bizNo);
    }
}
