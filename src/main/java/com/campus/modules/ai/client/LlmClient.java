package com.campus.modules.ai.client;

import com.campus.modules.ai.client.dto.LlmChatRequest;
import com.campus.modules.ai.client.dto.LlmChatResponse;
import com.campus.modules.ai.client.dto.LlmStreamConsumer;

/**
 * 统一大模型客户端抽象接口 (抽象工厂/策略模式产品接口)
 * 屏蔽不同 LLM 厂商协议细节 (SenseNova, Qwen, DeepSeek, LocalMock)
 */
public interface LlmClient {

    /**
     * 提供商标识 (例如: sensenova, qwen, deepseek, local)
     */
    String getProvider();

    /**
     * 该客户端当前是否可用 (是否配置了密钥且网络允许)
     */
    boolean isAvailable();

    /**
     * 同步/阻塞调用对话接口 (用于 Round 1 工具决策)
     *
     * @param request 对话请求
     * @return 对话响应
     */
    LlmChatResponse chat(LlmChatRequest request);

    /**
     * 流式调用对话接口 (用于 Round 2 自然语言组织生成)
     *
     * @param request  对话请求
     * @param consumer 流式回调消费器
     */
    void streamChat(LlmChatRequest request, LlmStreamConsumer consumer);
}
