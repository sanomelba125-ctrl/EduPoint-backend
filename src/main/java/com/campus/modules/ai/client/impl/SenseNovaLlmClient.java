package com.campus.modules.ai.client.impl;

import com.campus.modules.ai.client.AbstractOpenAiCompatibleLlmClient;
import com.campus.modules.ai.config.AiConfig;
import org.springframework.stereotype.Component;

/**
 * 商汤日日新 SenseNova 大模型客户端实现
 */
@Component("senseNovaLlmClient")
public class SenseNovaLlmClient extends AbstractOpenAiCompatibleLlmClient {

    public SenseNovaLlmClient(AiConfig aiConfig) {
        super(aiConfig);
    }

    @Override
    public String getProvider() {
        return "sensenova";
    }

    @Override
    protected String getDefaultBaseUrl() {
        return "https://api.sensenova.cn/compatible-mode/v1";
    }

    @Override
    protected String getDefaultModel() {
        return "SenseNova-6.8-Flash-Lite";
    }
}
