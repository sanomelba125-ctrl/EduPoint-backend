package com.campus.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 公共广播通知用户已读状态与隐藏关联实体
 * 对应 P2-9 读写分离架构，解耦个人私信与全校广播
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_notice_read")
public class SysNoticeRead {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 广播通知ID (关联 sys_notice.id) */
    private Long noticeId;

    /** 用户ID (关联 sys_user.id 或 student.id) */
    private Long userId;

    /** 用户类型: 1-管理员 2-学生 */
    private Integer userType;

    /** 用户视角软删除/隐藏标记: 0-正常 1-用户已隐藏 */
    private Integer isDeleted;

    /** 已读确认时间 */
    private LocalDateTime readTime;

    /** 记录创建时间 */
    private LocalDateTime createTime;

    /** 记录更新时间 */
    private LocalDateTime updateTime;
}
