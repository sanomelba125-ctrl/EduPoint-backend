package com.campus.modules.student.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理端全量积分对账检索请求 DTO
 */
@Data
public class AdminPointRecordQueryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer page = 1;
    private Integer size = 10;

    /** 综合检索关键字 (学号 / 姓名 / 流水号 / 备注) */
    private String keyword;

    /** 学号 */
    private String studentNo;

    /** 学生姓名 */
    private String studentName;

    /** 流水单号 */
    private String recordNo;

    /** 业务行为类型: 1-打卡奖励 2-商城兑换 3-管理调账 4-订单取消返还 */
    private Integer type;
}
