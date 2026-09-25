package com.campus.modules.ai.client.dto;

import java.util.List;
import java.util.Map;

/**
 * 大模型对话请求契约 (Java 17 record)
 */
public record LlmChatRequest(
        String model,
        List<Map<String, Object>> messages,
        Object tools,
        String toolChoice,
        Double temperature,
        Integer maxTokens,
        String reasoningEffort,
        boolean stream
) {
    public static LlmChatRequest of(String model, List<Map<String, Object>> messages, Object tools) {
        return new LlmChatRequest(model, messages, tools, "auto", 0.2, 4096, "none", false);
    }

    public static LlmChatRequest ofStream(String model, List<Map<String, Object>> messages) {
        return new LlmChatRequest(model, messages, null, null, 0.2, 4096, "none", true);
    }
}
