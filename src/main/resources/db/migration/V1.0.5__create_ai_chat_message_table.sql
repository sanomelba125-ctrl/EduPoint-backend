-- ===================================================================
-- V1.0.5: 创建 AI 智能问答消息历史记录表 (ai_chat_message)
-- 满足学生与智能客服多轮对话持久化、历史还原与 Function Calling 轨迹复现需求
-- ===================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `ai_chat_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息主键ID',
    `student_id` BIGINT DEFAULT NULL COMMENT '关联学生主键ID(已登录学生绑定；未登录或游客为NULL)',
    `conversation_id` VARCHAR(64) DEFAULT NULL COMMENT '会话分组标识(支持多轮或按会话清除)',
    `role` VARCHAR(20) NOT NULL COMMENT '消息角色: user-用户提问, assistant-助手回复, system-系统提示',
    `content` TEXT NOT NULL COMMENT '对话正文内容(Markdown或纯文本)',
    `source` VARCHAR(30) DEFAULT NULL COMMENT '应答引擎来源: QWEN / SENSENOVA / TOOL_AGENT / LOCAL_AGENT / KNOWLEDGE_BASE',
    `tool_call_json` MEDIUMTEXT DEFAULT NULL COMMENT 'Function Calling 工具调用与执行轨迹详情JSON',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消息创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '消息更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记: 0-正常 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_student_create` (`student_id`, `create_time`),
    KEY `idx_conversation` (`conversation_id`),
    KEY `idx_student_deleted` (`student_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI智能客服问答历史记录表';
