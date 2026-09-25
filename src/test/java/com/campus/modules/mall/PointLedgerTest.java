package com.campus.modules.mall;

import com.campus.modules.mall.entity.StudentPointRecord;
import com.campus.modules.mall.mapper.StudentPointRecordMapper;
import com.campus.modules.mall.service.PointLedger;
import com.campus.modules.mall.service.impl.PointLedgerImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 统一积分流水账本不变量单元测试
 */
class PointLedgerTest {

    private StudentPointRecordMapper mapper;
    private PointLedger pointLedger;

    @BeforeEach
    void setUp() {
        mapper = mock(StudentPointRecordMapper.class);
        pointLedger = new PointLedgerImpl(mapper);
    }

    @Test
    @DisplayName("测试积分消耗流水入账与数学恒等推导: before + change == after")
    void testPointLedgerDeductInvariant() {
        ArgumentCaptor<StudentPointRecord> captor = ArgumentCaptor.forClass(StudentPointRecord.class);

        // 兑换消耗 30 积分，变动后余额 70
        pointLedger.record(1001L, 2, -30, 70, "兑换礼品", 501L, null);

        verify(mapper, times(1)).insert(captor.capture());
        StudentPointRecord record = captor.getValue();

        assertNotNull(record);
        assertEquals(1001L, record.getStudentId());
        assertEquals(2, record.getType());
        assertEquals(-30, record.getChangePoints());
        assertEquals(70, record.getAfterPoints());
        // 关键不变量检验：beforePoints 必须严格为 100
        assertEquals(100, record.getBeforePoints());
        assertEquals(record.getBeforePoints() + record.getChangePoints(), record.getAfterPoints());
        // null operatorId 自动归一化为 0L
        assertEquals(0L, record.getOperatorId());
        assertEquals("兑换礼品", record.getDescription());
        // 自动派生业务幂等单据号: GIFT-ORD-{refId}
        assertEquals("GIFT-ORD-501", record.getBizNo());
    }

    @Test
    @DisplayName("测试积分奖励流水入账与数学恒等推导: before + change == after")
    void testPointLedgerRewardInvariant() {
        ArgumentCaptor<StudentPointRecord> captor = ArgumentCaptor.forClass(StudentPointRecord.class);

        // 打卡奖励 +50 积分，变动后余额 150
        pointLedger.record(1002L, 1, 50, 150, "活动打卡奖励【迎新晚会】", 201L, 0L);

        verify(mapper, times(1)).insert(captor.capture());
        StudentPointRecord record = captor.getValue();

        assertNotNull(record);
        assertEquals(1002L, record.getStudentId());
        assertEquals(1, record.getType());
        assertEquals(50, record.getChangePoints());
        assertEquals(150, record.getAfterPoints());
        // 关键不变量检验：beforePoints 必须严格为 100
        assertEquals(100, record.getBeforePoints());
        assertEquals(record.getBeforePoints() + record.getChangePoints(), record.getAfterPoints());
        assertEquals(0L, record.getOperatorId());
        // 自动派生业务幂等单据号: ACT-CHK-{refId}
        assertEquals("ACT-CHK-201", record.getBizNo());
    }

    @Test
    @DisplayName("测试显式传入业务单据号 bizNo：确保入账记录 bizNo 严格一致")
    void testPointLedgerExplicitBizNo() {
        ArgumentCaptor<StudentPointRecord> captor = ArgumentCaptor.forClass(StudentPointRecord.class);

        pointLedger.record(1003L, 2, -100, 200, "兑换大礼包", "ORD202609230001", 999L, null);

        verify(mapper, times(1)).insert(captor.capture());
        StudentPointRecord record = captor.getValue();

        assertNotNull(record);
        assertEquals("ORD202609230001", record.getBizNo());
        assertEquals(999L, record.getRefId());
    }

    @Test
    @DisplayName("测试 uk_biz_type 唯一键冲突：DuplicateKeyException 必须向上抛出以触发外层事务回滚")
    void testPointLedgerDuplicateKeyThrows() {
        doThrow(new org.springframework.dao.DuplicateKeyException("Duplicate entry 'ORD202609230001-2' for key 'uk_biz_type'"))
                .when(mapper).insert(any(StudentPointRecord.class));

        assertThrows(org.springframework.dao.DuplicateKeyException.class, () ->
                pointLedger.record(1003L, 2, -100, 200, "重复兑换", "ORD202609230001", 999L, null)
        );
    }
}
