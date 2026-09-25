package com.campus.modules.ai.client.dto;

import java.util.Map;

/**
 * 大模型流式输出片段 (Java 17 record)
 */
public record LlmStreamChunk(
        String content,
        boolean finish,
        String role,
        Map<String, Object> toolCall
) {
    public static LlmStreamChunk text(String content, boolean finish) {
        return new LlmStreamChunk(content, finish, "assistant", null);
    }

    public static LlmStreamChunk done() {
        return new LlmStreamChunk("", true, "assistant", null);
    }
}
