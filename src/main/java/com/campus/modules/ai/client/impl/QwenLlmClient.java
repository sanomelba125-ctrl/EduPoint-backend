package com.campus.modules.ai.client.impl;

import com.campus.modules.ai.client.AbstractOpenAiCompatibleLlmClient;
import com.campus.modules.ai.config.AiConfig;
import org.springframework.stereotype.Component;

/**
 * 阿里通义千问 Qwen 大模型客户端实现 (基于 DashScope OpenAI 兼容接口)
 */
@Component("qwenLlmClient")
public class QwenLlmClient extends AbstractOpenAiCompatibleLlmClient {

    public QwenLlmClient(AiConfig aiConfig) {
        super(aiConfig);
    }

    @Override
    public String getProvider() {
        return "qwen";
    }

    @Override
    protected String getDefaultBaseUrl() {
        return "https://dashscope.aliyuncs.com/compatible-mode/v1";
    }

    @Override
    protected String getDefaultModel() {
        return "qwen-plus";
    }
}
