package com.campus.modules.ai;

import com.campus.modules.ai.client.LlmClient;
import com.campus.modules.ai.client.LlmClientFactory;
import com.campus.modules.ai.client.dto.LlmChatRequest;
import com.campus.modules.ai.client.dto.LlmChatResponse;
import com.campus.modules.ai.client.dto.LlmStreamConsumer;
import com.campus.modules.ai.client.impl.DeepSeekLlmClient;
import com.campus.modules.ai.client.impl.LocalMockLlmClient;
import com.campus.modules.ai.client.impl.QwenLlmClient;
import com.campus.modules.ai.client.impl.SenseNovaLlmClient;
import com.campus.modules.ai.config.AiConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("P5-10 抽象工厂模式：大模型多厂商适配与降级测试")
class LlmClientFactoryTest {

    private AiConfig aiConfig;
    private SenseNovaLlmClient senseNovaClient;
    private QwenLlmClient qwenClient;
    private DeepSeekLlmClient deepSeekClient;
    private LocalMockLlmClient localMockClient;
    private LlmClientFactory factory;

    @BeforeEach
    void setUp() {
        aiConfig = new AiConfig();
        aiConfig.setEnabled(true);
        aiConfig.setProvider("sensenova");
        aiConfig.setApiKey(""); // 初始未配密钥

        senseNovaClient = new SenseNovaLlmClient(aiConfig);
        qwenClient = new QwenLlmClient(aiConfig);
        deepSeekClient = new DeepSeekLlmClient(aiConfig);
        localMockClient = new LocalMockLlmClient();

        factory = new LlmClientFactory(aiConfig, List.of(
                senseNovaClient,
                qwenClient,
                deepSeekClient,
                localMockClient
        ));
    }

    @Test
    @DisplayName("工厂正确注册所有厂商客户端")
    void testRegisteredProviders() {
        assertEquals(4, factory.getRegisteredProviders().size());
        assertTrue(factory.getRegisteredProviders().contains("sensenova"));
        assertTrue(factory.getRegisteredProviders().contains("qwen"));
        assertTrue(factory.getRegisteredProviders().contains("deepseek"));
        assertTrue(factory.getRegisteredProviders().contains("local"));
    }

    @Test
    @DisplayName("未配ApiKey时自动降级至LocalMock客户端")
    void testAutoFallbackToLocalMockWhenNoApiKey() {
        LlmClient primary = factory.getPrimaryClient();
        assertNotNull(primary);
        assertEquals("local", primary.getProvider(), "外部大模型无 Key 时必须自动安全降级至本地 Mock 客户端");
    }

    @Test
    @DisplayName("配置有效ApiKey时正确选择指定厂商")
    void testSelectConfiguredProviderWithApiKey() {
        aiConfig.setApiKey("test-valid-sensenova-key");
        LlmClient primary = factory.getPrimaryClient();
        assertNotNull(primary);
        assertEquals("sensenova", primary.getProvider());
    }

    @Test
    @DisplayName("LocalMock 客户端同步决策与工具调用识别")
    void testLocalMockToolDecision() {
        LlmChatRequest req = new LlmChatRequest(
                "local",
                List.of(Map.of("role", "user", "content", "我想查一下打卡签到规则")),
                List.of(Map.of("name", "search_platform_knowledge")),
                "auto",
                0.2,
                100,
                "none",
                false
        );

        LlmChatResponse resp = localMockClient.chat(req);
        assertTrue(resp.success());
        assertTrue(resp.hasToolCalls(), "包含'规则'意图且有可用工具时，LocalMock 应当模拟生成 tool_calls");
        assertEquals("search_platform_knowledge", ((Map<?, ?>) resp.toolCalls().get(0).get("function")).get("name"));
    }

    @Test
    @DisplayName("LocalMock 客户端流式输出")
    void testLocalMockStreamChat() {
        LlmChatRequest req = new LlmChatRequest(
                "local",
                List.of(Map.of("role", "user", "content", "你好呀")),
                null,
                null,
                0.2,
                100,
                "none",
                true
        );

        StringBuilder output = new StringBuilder();
        AtomicBoolean completed = new AtomicBoolean(false);

        localMockClient.streamChat(req, new LlmStreamConsumer() {
            @Override
            public void onChunk(String deltaText, boolean finish) {
                output.append(deltaText);
            }

            @Override
            public void onComplete() {
                completed.set(true);
            }
        });

        assertTrue(completed.get());
        assertTrue(output.toString().contains("小智"));
    }
}
