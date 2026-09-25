package com.campus.modules.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生积分明细台账流水实体
 * 对应需求: 3.6 积分管理 / 4. 数据需求
 */
@Data
@TableName("student_point_record")
public class StudentPointRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学生ID */
    private Long studentId;

    /** 变动业务类型: 1-活动打卡奖励(+) 2-商城兑换消费(-) 3-管理员手动调分(±) 4-订单取消返还(+) */
    private Integer type;

    /** 变动积分值(正数增加，负数扣减) */
    private Integer changePoints;

    /** 变动前可用积分余额 */
    private Integer beforePoints;

    /** 变动后可用积分余额 */
    private Integer afterPoints;

    /** 积分变动业务明细说明 */
    private String description;

    /** 关联业务ID(如 student_activity.id 或 student_gift.id) */
    private Long refId;

    /** 业务幂等单据号(订单号/打卡记录号/调账审批号)，与 type 共同构成 uk_biz_type 联合唯一索引 */
    private String bizNo;

    /** 操作人ID(关联 sys_user.id，系统自动触发则为 0 或 NULL) */
    private Long operatorId;

    /** 流水记账时间 */
    private LocalDateTime createTime;

    /** 逻辑删除标记 */
    private Integer deleted;
}
