package com.campus.modules.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 事务性发件箱 (Transactional Outbox) 本地消息实体 (P1-3)
 */
@Data
@TableName("local_message")
public class LocalMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 全局唯一消息ID (UUID) */
    private String messageId;

    /** 业务类型 (如 GIFT_ORDER_CREATE, DELAY_CLOSE) */
    private String bizType;

    /** 业务唯一标识 (如 orderNo 或 requestId) */
    private String bizId;

    /** 序列化消息体 JSON (契约数据) */
    private String payload;

    /**
     * 消息投递状态:
     * 0-待投递 (PENDING)
     * 1-已投递待确认 (PUBLISHED)
     * 2-已确认完成 (CONSUMED)
     * 3-重试超限失败 (DEAD/FAILED)
     */
    private Integer status;

    /** 投递重试次数 */
    private Integer retryCount;

    /** 下一次重试时间 */
    private LocalDateTime nextRetryTime;

    /** 全链路追踪号 (MDC) */
    private String traceId;

    /** 失败原因/异常栈摘要 */
    private String failReason;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
