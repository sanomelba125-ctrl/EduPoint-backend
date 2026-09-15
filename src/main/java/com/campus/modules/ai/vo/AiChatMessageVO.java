package com.campus.modules.ai.vo;

import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 问答历史消息视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatMessageVO {

    /** 消息主键ID */
    private Long id;

    /** 关联学生ID */
    private Long studentId;

    /** 会话ID */
    private String conversationId;

    /** 角色: user / assistant / system */
    private String role;

    /** 消息正文 */
    private String content;

    /** 来源模型或引擎 */
    private String source;

    /** 工具调用与执行详情对象 (结构化解析) */
    private JSONObject toolCall;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 简短格式化时间 (例如 10:25) */
    private String time;
}
