package com.campus;

import com.campus.modules.mall.entity.PointRecordType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 积分流水业务类型枚举单元测试
 */
class PointRecordTypeTest {

    @Test
    @DisplayName("测试已知积分业务类型枚举映射")
    void testKnownPointRecordTypes() {
        assertEquals(PointRecordType.ACTIVITY_CHECKIN, PointRecordType.of(1));
        assertEquals("活动打卡奖励", PointRecordType.of(1).getDescription());

        assertEquals(PointRecordType.GIFT_EXCHANGE, PointRecordType.of(2));
        assertEquals("商城兑换消费", PointRecordType.of(2).getDescription());

        assertEquals(PointRecordType.ADMIN_ADJUST, PointRecordType.of(3));
        assertEquals("系统人工调账", PointRecordType.of(3).getDescription());

        assertEquals(PointRecordType.ORDER_REFUND, PointRecordType.of(4));
        assertEquals("订单取消返还", PointRecordType.of(4).getDescription());
    }

    @Test
    @DisplayName("测试未知或空业务类型容错兜底")
    void testUnknownPointRecordTypes() {
        assertEquals(PointRecordType.UNKNOWN, PointRecordType.of(null));
        assertEquals("其他流水变动", PointRecordType.of(null).getDescription());

        assertEquals(PointRecordType.UNKNOWN, PointRecordType.of(0));
        assertEquals(PointRecordType.UNKNOWN, PointRecordType.of(999));
        assertEquals(PointRecordType.UNKNOWN, PointRecordType.of(-1));
    }
}
