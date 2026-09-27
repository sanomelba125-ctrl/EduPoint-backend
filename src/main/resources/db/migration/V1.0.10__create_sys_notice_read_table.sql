-- ===================================================================
-- 协同模块：全校广播通知读写分离治理 (P2-9)
-- sys_notice_read: 公共广播通知用户已读状态与隐藏关联表
-- ===================================================================

CREATE TABLE IF NOT EXISTS `sys_notice_read` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    `notice_id` BIGINT NOT NULL COMMENT '广播通知ID (sys_notice.id)',
    `user_id` BIGINT NOT NULL COMMENT '已读用户ID',
    `user_type` TINYINT NOT NULL COMMENT '用户类型: 1-管理员 2-学生',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '用户视角软删除标记: 0-正常 1-用户已删除/隐藏',
    `read_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '已读确认时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_notice_user` (`notice_id`, `user_id`, `user_type`),
    INDEX `idx_user` (`user_id`, `user_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公共广播通知用户已读状态与隐藏关联表';
