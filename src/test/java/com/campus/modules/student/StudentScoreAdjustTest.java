package com.campus.modules.student;

import com.campus.common.exception.BusinessException;
import com.campus.modules.mall.entity.StudentPointRecord;
import com.campus.modules.mall.mapper.StudentPointRecordMapper;
import com.campus.modules.mall.service.PointLedger;
import com.campus.modules.mall.service.impl.PointLedgerImpl;
import com.campus.modules.student.dto.PointAdjustDTO;
import com.campus.modules.student.entity.Student;
import com.campus.modules.student.mapper.StudentMapper;
import com.campus.modules.student.service.impl.StudentScoreServiceImpl;
import com.campus.modules.system.service.SysNoticeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 管理员人工积分调账单测 (StudentScoreAdjustTest)
 */
class StudentScoreAdjustTest {

    private StudentMapper studentMapper;
    private StudentPointRecordMapper pointRecordMapper;
    private PointLedger pointLedger;
    private SysNoticeService sysNoticeService;

    private StudentScoreServiceImpl studentScoreService;

    @BeforeEach
    void setUp() {
        studentMapper = mock(StudentMapper.class);
        pointRecordMapper = mock(StudentPointRecordMapper.class);
        sysNoticeService = mock(SysNoticeService.class);

        // 使用真实的 PointLedgerImpl 组装，以便直接验证最终落库到 student_point_record 的审计字段
        pointLedger = new PointLedgerImpl(pointRecordMapper);

        studentScoreService = new StudentScoreServiceImpl(
                studentMapper,
                pointRecordMapper,
                pointLedger,
                sysNoticeService
        );
    }

    @Test
    @DisplayName("测试正数调账（奖励）：学生当前积分 +100，累计总分 +100，写入流水类型 3（人工调账）")
    void testAdjustPoints_PositiveReward_Success() {
        Long studentId = 1001L;
        Long adminId = 88L;

        Student student = new Student();
        student.setId(studentId);
        student.setCurrentPoints(50);
        student.setTotalPoints(120);
        student.setDeleted(0);

        when(studentMapper.selectById(studentId)).thenReturn(student);
        when(studentMapper.addCurrentPoints(eq(studentId), eq(100))).thenReturn(1);
        when(studentMapper.addTotalPoints(eq(studentId), eq(100))).thenReturn(1);

        PointAdjustDTO dto = new PointAdjustDTO();
        dto.setStudentId(studentId);
        dto.setPoints(100);
        dto.setReason("参加校庆志愿服务突出奖励");
        dto.setApprovalNo("SP-2026-001");

        assertDoesNotThrow(() -> studentScoreService.adjustPoints(dto, adminId));

        // 验证当前可用积分 +100
        verify(studentMapper, times(1)).addCurrentPoints(eq(studentId), eq(100));
        // 验证历史累计总分 +100（光荣榜荣誉分同步累加）
        verify(studentMapper, times(1)).addTotalPoints(eq(studentId), eq(100));

        // 验证写入流水类型 3（人工调账）与台账计算
        ArgumentCaptor<StudentPointRecord> recordCaptor = ArgumentCaptor.forClass(StudentPointRecord.class);
        verify(pointRecordMapper, times(1)).insert(recordCaptor.capture());

        StudentPointRecord record = recordCaptor.getValue();
        assertNotNull(record);
        assertEquals(studentId, record.getStudentId());
        assertEquals(3, record.getType(), "流水类型必须为 3 (系统人工调账)");
        assertEquals(100, record.getChangePoints(), "变动分值应为 +100");
        assertEquals(50, record.getBeforePoints(), "变动前积分应为 50");
        assertEquals(150, record.getAfterPoints(), "变动后积分应为 150");
        assertTrue(record.getDescription().contains("SP-2026-001"));
        assertTrue(record.getDescription().contains("参加校庆志愿服务突出奖励"));
    }

    @Test
    @DisplayName("测试负数调账（核减）：可用积分不足以核减时（如当前 20 分欲扣 50 分），抛出异常防止扣成负分")
    void testAdjustPoints_NegativeDeduction_InsufficientBalance_ShouldThrowException() {
        Long studentId = 1002L;
        Long adminId = 88L;

        Student student = new Student();
        student.setId(studentId);
        student.setCurrentPoints(20);
        student.setTotalPoints(50);
        student.setDeleted(0);

        when(studentMapper.selectById(studentId)).thenReturn(student);

        PointAdjustDTO dto = new PointAdjustDTO();
        dto.setStudentId(studentId);
        dto.setPoints(-50); // 扣 50 分，但当前只有 20 分
        dto.setReason("违纪行为核减积分");

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            studentScoreService.adjustPoints(dto, adminId);
        });

        assertTrue(ex.getMessage().contains("变负数") || ex.getMessage().contains("系统拦截"),
                "应抛出防止扣减为负数的拦截异常，实际为: " + ex.getMessage());

        // 验证未调用底层扣减和记账
        verify(studentMapper, never()).deductCurrentPoints(anyLong(), anyInt());
        verify(pointRecordMapper, never()).insert(any(StudentPointRecord.class));
    }

    @Test
    @DisplayName("测试负数调账正常扣减：可用积分扣减 30，历史累计总分不被削减，记账流水类型为 3")
    void testAdjustPoints_NegativeDeduction_Success() {
        Long studentId = 1003L;
        Long adminId = 88L;

        Student student = new Student();
        student.setId(studentId);
        student.setCurrentPoints(100);
        student.setTotalPoints(200);
        student.setDeleted(0);

        when(studentMapper.selectById(studentId)).thenReturn(student);
        when(studentMapper.deductCurrentPoints(eq(studentId), eq(30))).thenReturn(1);

        PointAdjustDTO dto = new PointAdjustDTO();
        dto.setStudentId(studentId);
        dto.setPoints(-30);
        dto.setReason("撤销重复申报的加分项");

        assertDoesNotThrow(() -> studentScoreService.adjustPoints(dto, adminId));

        // 验证当前可用积分原子扣减 30
        verify(studentMapper, times(1)).deductCurrentPoints(eq(studentId), eq(30));
        // 验证历史累计积分不变（不减总荣誉分）
        verify(studentMapper, never()).addTotalPoints(anyLong(), anyInt());

        // 验证写入流水
        ArgumentCaptor<StudentPointRecord> recordCaptor = ArgumentCaptor.forClass(StudentPointRecord.class);
        verify(pointRecordMapper, times(1)).insert(recordCaptor.capture());

        StudentPointRecord record = recordCaptor.getValue();
        assertEquals(3, record.getType());
        assertEquals(-30, record.getChangePoints());
        assertEquals(100, record.getBeforePoints());
        assertEquals(70, record.getAfterPoints());
    }

    @Test
    @DisplayName("测试调账审计：操作员 ID (adminId) 必须被严格记录到 student_point_record")
    void testAdjustPoints_AuditLog_OperatorIdStrictlyRecorded() {
        Long studentId = 1004L;
        Long operatorAdminId = 999L; // 操作员管理员 ID

        Student student = new Student();
        student.setId(studentId);
        student.setCurrentPoints(80);
        student.setTotalPoints(100);
        student.setDeleted(0);

        when(studentMapper.selectById(studentId)).thenReturn(student);
        when(studentMapper.addCurrentPoints(eq(studentId), eq(20))).thenReturn(1);
        when(studentMapper.addTotalPoints(eq(studentId), eq(20))).thenReturn(1);

        PointAdjustDTO dto = new PointAdjustDTO();
        dto.setStudentId(studentId);
        dto.setPoints(20);
        dto.setReason("审计合规测试调账");

        studentScoreService.adjustPoints(dto, operatorAdminId);

        ArgumentCaptor<StudentPointRecord> recordCaptor = ArgumentCaptor.forClass(StudentPointRecord.class);
        verify(pointRecordMapper, times(1)).insert(recordCaptor.capture());

        StudentPointRecord savedRecord = recordCaptor.getValue();
        assertNotNull(savedRecord);
        assertEquals(operatorAdminId, savedRecord.getOperatorId(),
                "操作员 ID 必须被严格记录到 student_point_record 表中以供审计追溯");
    }

    @Test
    @DisplayName("测试调账分值为 0 时应拦截并抛出异常")
    void testAdjustPoints_ZeroPoints_ShouldThrowException() {
        Long studentId = 1005L;
        Student student = new Student();
        student.setId(studentId);
        student.setCurrentPoints(50);
        student.setDeleted(0);
        when(studentMapper.selectById(studentId)).thenReturn(student);

        PointAdjustDTO dto = new PointAdjustDTO();
        dto.setStudentId(studentId);
        dto.setPoints(0);
        dto.setReason("零分调账测试");

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            studentScoreService.adjustPoints(dto, 1L);
        });

        assertTrue(ex.getMessage().contains("不能为 0"));
        verify(studentMapper, never()).addCurrentPoints(anyLong(), anyInt());
        verify(studentMapper, never()).deductCurrentPoints(anyLong(), anyInt());
        verify(pointRecordMapper, never()).insert(any(StudentPointRecord.class));
    }
}
