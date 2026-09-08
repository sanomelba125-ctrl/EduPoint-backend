package com.campus.modules.student.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 学生积分资产概要（双积分模型）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentScoreBalanceVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 可用积分余额（消费型，用于商城兑换） */
    private Integer currentPoints;

    /** 历史累计积分（荣誉型，用于光荣榜与评优） */
    private Integer totalPoints;

    /** 全校光荣榜当前排位 */
    private Integer rank;
}
