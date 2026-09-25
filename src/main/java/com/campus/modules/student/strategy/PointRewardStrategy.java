package com.campus.modules.student.strategy;

/**
 * 积分变动与奖励计算策略接口
 * 落地核心设计模式：策略模式 (Strategy Pattern)
 */
public interface PointRewardStrategy {

    /**
     * 该策略支持处理的积分变动类型
     * 1-活动打卡奖励, 2-礼品兑换扣除, 3-人工审批调账, 4-取消退单退还 (详见 PointRecordType)
     *
     * @param type 积分变动类型
     * @return true-支持当前类型
     */
    boolean supports(Integer type);

    /**
     * 执行具体积分变动计算与合法性校验
     *
     * @param context 积分上下文
     * @return 计算结果
     */
    PointCalculationResult calculate(PointContext context);
}
