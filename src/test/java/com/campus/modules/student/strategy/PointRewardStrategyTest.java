package com.campus.modules.student.strategy;

import com.campus.common.exception.BusinessException;
import com.campus.modules.student.strategy.impl.AdminAdjustStrategy;
import com.campus.modules.student.strategy.impl.CheckinRewardStrategy;
import com.campus.modules.student.strategy.impl.MallExchangeDeductStrategy;
import com.campus.modules.student.strategy.impl.OrderCancelRefundStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("P5-10 策略模式：积分结算与调账策略单元测试")
class PointRewardStrategyTest {

    private PointStrategyContext context;
    private CheckinRewardStrategy checkinStrategy;
    private MallExchangeDeductStrategy mallStrategy;
    private AdminAdjustStrategy adminStrategy;
    private OrderCancelRefundStrategy refundStrategy;

    @BeforeEach
    void setUp() {
        checkinStrategy = new CheckinRewardStrategy();
        mallStrategy = new MallExchangeDeductStrategy();
        adminStrategy = new AdminAdjustStrategy();
        refundStrategy = new OrderCancelRefundStrategy();

        context = new PointStrategyContext(List.of(
                checkinStrategy,
                mallStrategy,
                adminStrategy,
                refundStrategy
        ));
    }

    @Test
    @DisplayName("策略1：活动打卡增加可用与累计总积分")
    void testCheckinRewardStrategy() {
        PointContext ctx = new PointContext(100L, 50, 20, "ACT-CHK-10", 10L, 0L, "参与科技讲座打卡");
        PointCalculationResult result = context.execute(1, ctx);

        assertEquals(20, result.pointChange());
        assertEquals(50, result.beforePoints());
        assertEquals(70, result.afterPoints());
        assertTrue(result.addTotalPoints(), "打卡奖励必须同步增加累计总积分");
        assertEquals("ACT-CHK-10", result.bizNo());
    }

    @Test
    @DisplayName("策略1：活动打卡分值必须大于0")
    void testCheckinRewardStrategyZeroPoints() {
        PointContext ctx = new PointContext(100L, 50, 0, "ACT-CHK-10", 10L, 0L, "参与科技讲座打卡");
        assertThrows(BusinessException.class, () -> context.execute(1, ctx));
    }

    @Test
    @DisplayName("策略2：商城兑换扣减可用积分，不动累计总积分")
    void testMallExchangeDeductStrategy() {
        PointContext ctx = new PointContext(100L, 100, 30, "ORD-2026-001", 1L, 100L, "兑换文创笔记本");
        PointCalculationResult result = context.execute(2, ctx);

        assertEquals(-30, result.pointChange(), "兑换变更分值必须为负数");
        assertEquals(100, result.beforePoints());
        assertEquals(70, result.afterPoints());
        assertFalse(result.addTotalPoints(), "兑换扣减绝不能扣减累计总积分");
        assertEquals("ORD-2026-001", result.bizNo());
    }

    @Test
    @DisplayName("策略2：商城兑换余额不足抛出 BusinessException")
    void testMallExchangeInsufficientBalance() {
        PointContext ctx = new PointContext(100L, 20, 30, "ORD-2026-001", 1L, 100L, "兑换文创笔记本");
        BusinessException ex = assertThrows(BusinessException.class, () -> context.execute(2, ctx));
        assertTrue(ex.getMessage().contains("可用积分不足"));
    }

    @Test
    @DisplayName("策略3：管理员人工加分（荣誉奖励），同步增加累计总积分")
    void testAdminAdjustPositive() {
        PointContext ctx = new PointContext(100L, 50, 15, "ADJ-2026-001", null, 1L, "优秀志愿者表彰");
        PointCalculationResult result = context.execute(3, ctx);

        assertEquals(15, result.pointChange());
        assertEquals(50, result.beforePoints());
        assertEquals(65, result.afterPoints());
        assertTrue(result.addTotalPoints(), "人工奖励加分同步计入累计总积分");
        assertEquals("ADJ-2026-001", result.bizNo());
    }

    @Test
    @DisplayName("策略3：管理员人工扣分（违规惩罚），不动累计总积分")
    void testAdminAdjustNegative() {
        PointContext ctx = new PointContext(100L, 50, -10, "ADJ-2026-002", null, 1L, "无故缺席违约扣减");
        PointCalculationResult result = context.execute(3, ctx);

        assertEquals(-10, result.pointChange());
        assertEquals(50, result.beforePoints());
        assertEquals(40, result.afterPoints());
        assertFalse(result.addTotalPoints(), "惩罚扣分绝不能扣减历史荣誉累计积分");
    }

    @Test
    @DisplayName("策略3：管理员人工扣分超额时拦截")
    void testAdminAdjustNegativeExceedBalance() {
        PointContext ctx = new PointContext(100L, 5, -10, "ADJ-2026-003", null, 1L, "违规扣分");
        BusinessException ex = assertThrows(BusinessException.class, () -> context.execute(3, ctx));
        assertTrue(ex.getMessage().contains("当前余额仅剩: 5 积分"));
    }

    @Test
    @DisplayName("策略4：订单取消全额返还可用积分，不动累计总积分")
    void testOrderCancelRefundStrategy() {
        PointContext ctx = new PointContext(100L, 70, 30, "REF-2026-001", 1L, 1L, "兑换订单取消返还");
        PointCalculationResult result = context.execute(4, ctx);

        assertEquals(30, result.pointChange(), "返还分值为正数");
        assertEquals(70, result.beforePoints());
        assertEquals(100, result.afterPoints());
        assertFalse(result.addTotalPoints(), "订单取消返还属于支出退回，绝不能再次计入历史累计总积分");
    }

    @Test
    @DisplayName("策略上下文：未支持的业务类型抛出 BusinessException")
    void testUnsupportedType() {
        PointContext ctx = new PointContext(100L, 70, 10, null, null, null, null);
        assertThrows(BusinessException.class, () -> context.execute(99, ctx));
    }
}
