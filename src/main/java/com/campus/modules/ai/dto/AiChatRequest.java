package com.campus.modules.ai.dto;

import lombok.Data;

/**
 * AI 智能问答提问请求体
 */
@Data
public class AiChatRequest {

    /** 学生提问内容 */
    private String question;

    /** 可选会话 ID */
    private String conversationId;
}
