package com.campus.modules.ai.client.impl;

import com.campus.modules.ai.client.LlmClient;
import com.campus.modules.ai.client.dto.LlmChatRequest;
import com.campus.modules.ai.client.dto.LlmChatResponse;
import com.campus.modules.ai.client.dto.LlmStreamConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 本地智能 Mock 客户端 (离线/断网/测试与终极兜底降级)
 */
@Slf4j
@Component("localMockLlmClient")
public class LocalMockLlmClient implements LlmClient {

    /** 模拟打字流式输出分块延迟（毫秒），兜底路径降低 SSE 工作线程阻塞占用 */
    private static final long STREAM_CHUNK_DELAY_MS = 10L;

    @Override
    public String getProvider() {
        return "local";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public LlmChatResponse chat(LlmChatRequest request) {
        log.info("[LocalMock] 收到同步对话决策请求");
        // 如果包含可用工具，并且是查询平台知识的意图，模拟触发 search_platform_knowledge 工具调用
        String lastUserPrompt = extractLastUserMessage(request);
        if (request.tools() != null
                && (lastUserPrompt.contains("知识") || lastUserPrompt.contains("规则") || lastUserPrompt.contains("怎么"))) {
            Map<String, Object> toolCall = Map.of(
                    "id", "mock_call_" + System.currentTimeMillis(),
                    "type", "function",
                    "function", Map.of(
                            "name", "search_platform_knowledge",
                            "arguments", "{\"keyword\":\"" + lastUserPrompt + "\"}"
                    )
            );
            return LlmChatResponse.success(null, List.of(toolCall), "{\"mock\":true}");
        }

        // 无需调用工具，直接回复
        String answer = "同学你好！我是校园小助手小智（本地离线模式）。我可以为你解答校园活动、积分兑换及日常平台规则，请问有什么可以帮您？";
        return LlmChatResponse.success(answer, List.of(), "{\"mock\":true}");
    }

    @Override
    public void streamChat(LlmChatRequest request, LlmStreamConsumer consumer) {
        log.info("[LocalMock] 收到流式对话生成请求");
        String lastToolContent = extractLastToolMessage(request);
        String reply;
        if (lastToolContent != null && !lastToolContent.isBlank()) {
            reply = "【本地智能解读】根据系统实时查询到的数据：\n\n" + lastToolContent + "\n\n如需办理相关事务，请前往对应功能页面操作。";
        } else {
            reply = "同学你好！我是校园小助手小智。由于当前未连接外部大模型网络，已由系统本地智能引擎为你服务。";
        }

        int step = 6;
        for (int i = 0; i < reply.length(); i += step) {
            int end = Math.min(i + step, reply.length());
            String chunk = reply.substring(i, end);
            consumer.onChunk(chunk, end == reply.length());
            try {
                Thread.sleep(STREAM_CHUNK_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        consumer.onComplete();
    }

    private String extractLastUserMessage(LlmChatRequest request) {
        if (request == null || request.messages() == null) {
            return "";
        }
        for (int i = request.messages().size() - 1; i >= 0; i--) {
            Map<String, Object> m = request.messages().get(i);
            if ("user".equals(m.get("role"))) {
                Object c = m.get("content");
                return c != null ? c.toString() : "";
            }
        }
        return "";
    }

    private String extractLastToolMessage(LlmChatRequest request) {
        if (request == null || request.messages() == null) {
            return null;
        }
        for (int i = request.messages().size() - 1; i >= 0; i--) {
            Map<String, Object> m = request.messages().get(i);
            if ("tool".equals(m.get("role"))) {
                Object c = m.get("content");
                return c != null ? c.toString() : null;
            }
        }
        return null;
    }
}
