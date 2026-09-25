package com.campus.modules.student.strategy.impl;

import com.campus.common.exception.BusinessException;
import com.campus.modules.student.strategy.PointCalculationResult;
import com.campus.modules.student.strategy.PointContext;
import com.campus.modules.student.strategy.PointRewardStrategy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 策略实现：活动打卡积分奖励 (type = 1)
 * 规则：增加可用积分，同时同步增加累计总积分 (total_points)
 */
@Component
public class CheckinRewardStrategy implements PointRewardStrategy {

    @Override
    public boolean supports(Integer type) {
        return type != null && type == 1;
    }

    @Override
    public PointCalculationResult calculate(PointContext context) {
        int points = context.change() != null ? context.change() : 0;
        if (points <= 0) {
            throw new BusinessException("活动打卡积分奖励分值必须大于 0");
        }
        int before = context.currentPoints() != null ? context.currentPoints() : 0;
        int after = before + points;

        String remark = StringUtils.hasText(context.reason())
                ? context.reason()
                : "参与活动现场打卡奖励";

        String bizNo = StringUtils.hasText(context.bizNo())
                ? context.bizNo().trim()
                : "ACT-CHK-" + context.refId();

        return new PointCalculationResult(points, before, after, true, remark, bizNo);
    }
}
