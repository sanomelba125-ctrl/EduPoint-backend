package com.campus.modules.student.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理端人工积分调账请求 DTO
 */
@Data
public class PointAdjustDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 目标学生 ID */
    @NotNull(message = "请选择目标学生")
    private Long studentId;

    /** 调账积分变动值 (正数为增加，负数为扣除) */
    @NotNull(message = "请输入变动分值")
    private Integer points;

    /** 调账原因/事由说明 */
    @NotNull(message = "请输入调账原因")
    private String reason;

    /** 审批工单编号/依据 (例: OA-202609-081) */
    private String approvalNo;
}
