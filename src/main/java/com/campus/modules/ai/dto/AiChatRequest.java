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

    /** 模拟测试学生 ID (仅限管理员真机模拟器调试使用，普通学生提问时强制忽略以防越权) */
    private Long simulateStudentId;

    /** 模拟测试学生学号 (仅限管理员真机模拟器调试使用) */
    private String simulateStudentNo;
}
