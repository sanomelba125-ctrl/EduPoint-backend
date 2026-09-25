package com.campus.modules.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息消费强幂等防重表实体 (P1-4)
 */
@Data
@TableName("mq_consume_log")
public class MqConsumeLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 消息全局唯一标识 */
    private String messageId;

    /** 消费者服务名称/标识 */
    private String consumerName;

    /** 业务类型 (GIFT_ORDER_CREATE, etc.) */
    private String bizType;

    /** 关联业务单据号 */
    private String bizId;

    /** 1-消费成功, 2-业务已拒绝 */
    private Integer status;

    private LocalDateTime consumeTime;
}
