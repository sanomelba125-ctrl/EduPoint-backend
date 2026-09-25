package com.campus.modules.ai.client.dto;

import java.util.List;
import java.util.Map;

/**
 * 大模型对话响应契约 (Java 17 record)
 */
public record LlmChatResponse(
        int statusCode,
        String content,
        List<Map<String, Object>> toolCalls,
        String rawBody,
        boolean success
) {
    public static LlmChatResponse success(String content, List<Map<String, Object>> toolCalls, String rawBody) {
        return new LlmChatResponse(200, content, toolCalls, rawBody, true);
    }

    public static LlmChatResponse failure(int statusCode, String rawBody) {
        return new LlmChatResponse(statusCode, null, null, rawBody, false);
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
