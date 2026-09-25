package com.campus.modules.ai.client.impl;

import com.campus.modules.ai.client.AbstractOpenAiCompatibleLlmClient;
import com.campus.modules.ai.config.AiConfig;
import org.springframework.stereotype.Component;

/**
 * DeepSeek 大模型客户端实现 (基于 DeepSeek OpenAI 兼容接口)
 */
@Component("deepSeekLlmClient")
public class DeepSeekLlmClient extends AbstractOpenAiCompatibleLlmClient {

    public DeepSeekLlmClient(AiConfig aiConfig) {
        super(aiConfig);
    }

    @Override
    public String getProvider() {
        return "deepseek";
    }

    @Override
    protected String getDefaultBaseUrl() {
        return "https://api.deepseek.com";
    }

    @Override
    protected String getDefaultModel() {
        return "deepseek-chat";
    }
}
