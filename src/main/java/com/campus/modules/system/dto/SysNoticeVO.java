package com.campus.modules.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统业务通知视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysNoticeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 通知ID */
    private Long id;

    /** 接收方类型: 1-管理端(管理员) 2-学生端(学生) */
    private Integer receiverType;

    /** 接收人ID (0 代表全员广播) */
    private Long receiverId;

    /** 通知类型: new_activity, gift_exchange, activity_card, gift_send, points_adjusted, waitlist_promoted */
    private String type;

    /** 通知类型中文名称 */
    private String typeName;

    /** 通知标题 */
    private String title;

    /** 通知正文内容 */
    private String content;

    /** 关联业务单据ID (如 activityId, orderId) */
    private Long bizId;

    /** 已读状态: 0-未读, 1-已读 */
    private Integer isRead;

    /** 阅读确认时间 */
    private LocalDateTime readTime;

    /** 消息发送时间 */
    private LocalDateTime createTime;
}
