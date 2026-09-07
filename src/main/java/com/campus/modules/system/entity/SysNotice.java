package com.campus.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统业务通知记录实体
 * 对应需�? 3.13 实时通知 / 4. 数据需�? */
@Data
@TableName("sys_notice")
public class SysNotice {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收方类�? 1-管理�?管理�? 2-学生�?学生) */
    private Integer receiverType;

    /** 接收人ID(关联 sys_user.id �?student.id, 0代表全员广播) */
    private Long receiverId;

    /** 通知类型: new_activity-新活�? gift_exchange-礼物兑换, activity_card-活动打卡, gift_send-礼物送达, waitlist_promoted-候补递补 */
    private String type;

    /** 通知标题 */
    private String title;

    /** 通知消息正文内容 */
    private String content;

    /** 关联业务单据ID */
    private Long bizId;

    /** 已读状�? 0-未读 1-已读 */
    private Integer isRead;

    /** 阅读确认时间 */
    private LocalDateTime readTime;

    /** 消息发送时�?*/
    private LocalDateTime createTime;

    /** 逻辑删除标记 */
    private Integer deleted;
}
