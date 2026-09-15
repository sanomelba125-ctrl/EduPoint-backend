package com.campus.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 智能问答消息历史记录实体
 * 对应表: ai_chat_message
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_chat_message")
public class AiChatMessage {

    /** 消息主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联学生ID(已登录学生绑定；未登录或游客为 null) */
    private Long studentId;

    /** 会话分组标识(可选，用于多轮对话或分组管理) */
    private String conversationId;

    /** 消息角色: user / assistant / system */
    private String role;

    /** 对话正文内容(Markdown或纯文本) */
    private String content;

    /** 应答引擎来源: QWEN / SENSENOVA / TOOL_AGENT / LOCAL_AGENT / KNOWLEDGE_BASE */
    private String source;

    /** Function Calling 工具调用与执行轨迹详情JSON */
    private String toolCallJson;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记: 0-正常 1-已删除 */
    @TableLogic
    private Integer deleted;
}
