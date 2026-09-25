package com.campus.modules.student.strategy;

import com.campus.common.exception.BusinessException;
import com.campus.modules.mall.entity.PointRecordType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 积分策略执行器上下文 (Strategy Context)
 * 依赖注入全部 PointRewardStrategy 策略实现，消除 Service 中的 if-else 面条代码
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PointStrategyContext {

    private final List<PointRewardStrategy> strategies;

    /**
     * 启动自检：确保每种 PointRecordType 都有对应策略。
     */
    @PostConstruct
    void verifyAllTypesCovered() {
        if (strategies == null || strategies.isEmpty()) {
            throw new IllegalStateException("未注入任何 PointRewardStrategy 实现");
        }
        for (PointRecordType type : PointRecordType.values()) {
            if (type == PointRecordType.UNKNOWN) {
                continue;
            }
            boolean covered = strategies.stream().anyMatch(s -> s.supports(type.getCode()));
            if (!covered) {
                throw new IllegalStateException(
                        "积分策略缺失：类型 " + type.getCode() + "(" + type.getDescription() + ") 没有对应的 PointRewardStrategy 实现");
            }
        }
        log.info("积分策略自检通过，已注册策略数: {}", strategies.size());
    }

    /**
     * 根据积分变动类型动态匹配并执行策略计算
     *
     * @param type    积分类型 (1-活动打卡, 2-礼品兑换, 3-人工调账, 4-取消退单)
     * @param context 积分计算上下文
     * @return 经过策略校验后的标准计算结果
     */
    public PointCalculationResult execute(Integer type, PointContext context) {
        if (type == null) {
            throw new BusinessException("积分变动类型不能为空");
        }
        for (PointRewardStrategy strategy : strategies) {
            if (strategy.supports(type)) {
                log.debug("命中积分策略: type={}, strategy={}", type, strategy.getClass().getSimpleName());
                return strategy.calculate(context);
            }
        }
        throw new BusinessException(String.format("未找到类型为 %d 的积分变动结算策略", type));
    }
}
