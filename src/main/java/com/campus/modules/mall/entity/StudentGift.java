package com.campus.modules.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生礼品兑换订单与发放流转实体
 * 对应需求: 3.9 礼物兑换 / 3.10 兑换订单管理 / 4. 数据需求
 */
@Data
@TableName("student_gift")
public class StudentGift {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务订单编号(全局唯一防重复) */
    private String orderNo;

    /** 兑换学生ID */
    private Long studentId;

    /** 兑换礼品ID */
    private Long giftId;

    /** 兑换礼品数量 */
    private Integer quantity;

    /** 实际扣减积分 */
    private Integer pointsCost;

    /** 收件人真实姓名 */
    private String receiverName;

    /** 收件人联系电话 */
    private String receiverPhone;

    /** 收货地址或学生宿舍信息 */
    private String receiverAddress;

    /** 核销提货码(唯一凭据) */
    private String pickupCode;

    /** 订单状态: 1-待发放 2-已送达/已核销 3-已取消 */
    private Integer status;

    /** 订单取消时积分退还状态: 0-未退/不退 1-已全额退还 */
    private Integer refundPointsStatus;

    /** 实际送达/核销发放时间 */
    private LocalDateTime deliveryTime;

    /** 订单取消时间 */
    private LocalDateTime cancelTime;

    /** 取消原因说明 */
    private String cancelReason;

    /** 经办管理员ID(追溯责任人) */
    private Long operatorId;

    /** 发放备注/签收记录 */
    private String remark;

    /** 下单兑换时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记 */
    private Integer deleted;
}