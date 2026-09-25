package com.campus.modules.student.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 积分变动明细流水视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentPointRecordVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 流水主键 ID */
    private Long id;

    /** 财务对账流水编号 (例: PT-20260908-001) */
    private String recordNo;

    /** 学生 ID */
    private Long studentId;

    /** 学号 */
    private String studentNo;

    /** 学生姓名 */
    private String studentName;

    /** 学院 */
    private String college;

    /** 班级 */
    private String className;

    /** 变动业务类型: 1-活动打卡奖励(+) 2-商城兑换消费(-) 3-管理员手动调整(±) 4-订单取消返还(+) */
    private Integer type;

    /** 变动类型说明 (打卡加分 / 礼品兑换 / 系统调账 / 退单返还) */
    private String typeDesc;

    /** 变动积分值 (正数增加，负数扣除) */
    private Integer changePoints;

    /** 变动前可用积分余额 */
    private Integer beforePoints;

    /** 变动后可用积分余额 */
    private Integer afterPoints;

    /** 积分变动具体事由说明 */
    private String description;

    /** 关联业务 ID (活动参与ID / 兑换订单ID) */
    private Long refId;

    /** 业务幂等单据号 (例: GIFT-20260908-01 或 ACT-CHK-1001) */
    private String bizNo;

    /** 业务单据标识 (例: ACT-2026-001 或 ORD-20260908-01) */
    private String businessId;

    /** 业务单据分类 (ACTIVITY / GIFT / SYSTEM) */
    private String businessType;

    /** 操作人 ID (0 为系统自动触发) */
    private Long operatorId;

    /** 操作人姓名或账号 (如 admin_super 或 SYSTEM_AUTO) */
    private String operatorName;

    /** 记账创建时间 */
    private LocalDateTime createTime;
}
