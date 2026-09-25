package com.campus.modules.ai.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.campus.modules.ai.client.dto.LlmChatRequest;
import com.campus.modules.ai.client.dto.LlmChatResponse;
import com.campus.modules.ai.client.dto.LlmStreamConsumer;
import com.campus.modules.ai.config.AiConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议大模型基类 (适用于 SenseNova, Qwen, DeepSeek 等)
 */
@Slf4j
public abstract class AbstractOpenAiCompatibleLlmClient implements LlmClient {

    protected final AiConfig aiConfig;
    protected final HttpClient httpClient;

    protected AbstractOpenAiCompatibleLlmClient(AiConfig aiConfig) {
        this.aiConfig = aiConfig;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    protected abstract String getDefaultBaseUrl();

    protected abstract String getDefaultModel();

    protected String getEffectiveBaseUrl() {
        if (StringUtils.hasText(aiConfig.getBaseUrl()) && getProvider().equalsIgnoreCase(aiConfig.getProvider())) {
            return aiConfig.getBaseUrl();
        }
        return getDefaultBaseUrl();
    }

    protected String getEffectiveModel(LlmChatRequest request) {
        if (request != null && StringUtils.hasText(request.model())) {
            return request.model();
        }
        if (StringUtils.hasText(aiConfig.getModel()) && getProvider().equalsIgnoreCase(aiConfig.getProvider())) {
            return aiConfig.getModel();
        }
        return getDefaultModel();
    }

    protected String getEffectiveApiKey() {
        return aiConfig.getApiKey() != null ? aiConfig.getApiKey().trim() : "";
    }

    @Override
    public boolean isAvailable() {
        return aiConfig.isEnabled() && StringUtils.hasText(getEffectiveApiKey());
    }

    protected String normalizeEndpoint(String baseUrl) {
        String endpoint = StringUtils.hasText(baseUrl) ? baseUrl.trim() : getDefaultBaseUrl();
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        if (!endpoint.endsWith("/chat/completions")) {
            endpoint = endpoint + "/chat/completions";
        }
        return endpoint;
    }

    @Override
    public LlmChatResponse chat(LlmChatRequest request) {
        String endpoint = normalizeEndpoint(getEffectiveBaseUrl());
        JSONObject body = buildRequestBody(request, false);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(aiConfig.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + getEffectiveApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString(), StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> resp;
            try {
                resp = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (Exception ex) {
                log.warn("[{}] Round 1 握手首次连接异常，尝试快速重试: {}", getProvider(), ex.getMessage());
                resp = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            }

            if (resp.statusCode() != 200) {
                log.warn("[{}] Round 1 返回非200状态: status={}, body={}", getProvider(), resp.statusCode(), resp.body());
                return LlmChatResponse.failure(resp.statusCode(), resp.body());
            }

            JSONObject respJson = JSON.parseObject(resp.body());
            JSONArray choices = respJson.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                log.warn("[{}] choices 为空", getProvider());
                return LlmChatResponse.failure(resp.statusCode(), resp.body());
            }

            JSONObject choice0 = choices.getJSONObject(0);
            JSONObject assistantMsg = choice0.getJSONObject("message");
            if (assistantMsg == null) {
                return LlmChatResponse.failure(resp.statusCode(), resp.body());
            }

            String directContent = assistantMsg.getString("content");
            JSONArray tcArray = assistantMsg.getJSONArray("tool_calls");
            List<Map<String, Object>> toolCalls = new ArrayList<>();
            if (tcArray != null) {
                for (int i = 0; i < tcArray.size(); i++) {
                    toolCalls.add(tcArray.getJSONObject(i).toJavaObject(Map.class));
                }
            }

            return LlmChatResponse.success(directContent, toolCalls, resp.body());
        } catch (Exception e) {
            log.error("[{}] Round 1 同步对话执行异常: {}", getProvider(), e.getMessage(), e);
            return LlmChatResponse.failure(500, e.getMessage());
        }
    }

    @Override
    public void streamChat(LlmChatRequest request, LlmStreamConsumer consumer) {
        String endpoint = normalizeEndpoint(getEffectiveBaseUrl());
        JSONObject body = buildRequestBody(request, true);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(aiConfig.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + getEffectiveApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString(), StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<java.io.InputStream> resp;
            try {
                resp = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            } catch (Exception ex) {
                log.warn("[{}] Round 2 流式连接首次异常，快速重试: {}", getProvider(), ex.getMessage());
                resp = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            }

            if (resp.statusCode() != 200) {
                log.warn("[{}] Round 2 返回非200状态: status={}", getProvider(), resp.statusCode());
                consumer.onError(new RuntimeException("LLM stream returned HTTP " + resp.statusCode()));
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith(":")) {
                        continue;
                    }
                    if (line.equals("data: [DONE]") || line.equals("data:[DONE]")) {
                        consumer.onChunk("", true);
                        break;
                    }
                    if (line.startsWith("data:")) {
                        String jsonStr = line.substring(5).trim();
                        try {
                            JSONObject obj = JSON.parseObject(jsonStr);
                            JSONArray cList = obj.getJSONArray("choices");
                            if (cList != null && !cList.isEmpty()) {
                                JSONObject delta = cList.getJSONObject(0).getJSONObject("delta");
                                if (delta != null && delta.containsKey("content")) {
                                    String text = delta.getString("content");
                                    if (StringUtils.hasText(text)) {
                                        consumer.onChunk(text, false);
                                    }
                                }
                            }
                        } catch (Exception jsonEx) {
                            log.warn("[{}] 解析流式 SSE chunk 异常: {}", getProvider(), jsonEx.getMessage());
                        }
                    }
                }
            }
            consumer.onComplete();
        } catch (Exception e) {
            log.error("[{}] Round 2 流式对话执行异常: {}", getProvider(), e.getMessage(), e);
            consumer.onError(e);
        }
    }

    protected JSONObject buildRequestBody(LlmChatRequest request, boolean stream) {
        JSONObject body = new JSONObject();
        body.put("model", getEffectiveModel(request));
        body.put("stream", stream);
        if (request.maxTokens() != null) {
            body.put("max_tokens", request.maxTokens());
        }
        if (request.temperature() != null) {
            body.put("temperature", request.temperature());
        }
        if ("none".equalsIgnoreCase(request.reasoningEffort()) || (request.model() != null && request.model().toLowerCase().contains("qwen"))) {
            body.put("enable_thinking", false);
        }
        body.put("messages", request.messages());
        if (request.tools() != null) {
            body.put("tools", request.tools());
            body.put("tool_choice", request.toolChoice() != null ? request.toolChoice() : "auto");
        }
        return body;
    }
}
