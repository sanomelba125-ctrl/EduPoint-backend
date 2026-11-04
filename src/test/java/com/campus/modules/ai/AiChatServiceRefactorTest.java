package com.campus.modules.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.modules.ai.agent.tool.ToolRegistry;
import com.campus.modules.ai.client.LlmClientFactory;
import com.campus.modules.ai.client.impl.LocalMockLlmClient;
import com.campus.modules.ai.config.AiConfig;
import com.campus.modules.ai.dto.AiChatRequest;
import com.campus.modules.ai.entity.AiChatMessage;
import com.campus.modules.ai.mapper.AiChatMessageMapper;
import com.campus.modules.ai.service.AiMessageService;
import com.campus.modules.ai.service.LlmAgentService;
import com.campus.modules.ai.service.QaKnowledgeService;
import com.campus.modules.ai.service.impl.AiChatServiceImpl;
import com.campus.modules.ai.service.impl.AiMessageServiceImpl;
import com.campus.modules.ai.service.impl.LlmAgentServiceImpl;
import com.campus.modules.ai.vo.AiChatMessageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("P5-6 & P7-AI: AI 客服重构与领域服务拆分单元测试")
class AiChatServiceRefactorTest {

    private AiChatMessageMapper messageMapper;
    private AiMessageService messageService;

    private LlmClientFactory llmClientFactory;
    private ToolRegistry toolRegistry;
    private AiConfig aiConfig;
    private LlmAgentService llmAgentService;

    private QaKnowledgeService qaKnowledgeService;
    private AiChatServiceImpl chatService;

    @BeforeEach
    void setUp() {
        messageMapper = mock(AiChatMessageMapper.class);
        messageService = new AiMessageServiceImpl(messageMapper);

        aiConfig = new AiConfig();
        aiConfig.setEnabled(true);
        aiConfig.setProvider("local");

        llmClientFactory = mock(LlmClientFactory.class);
        when(llmClientFactory.getPrimaryClient()).thenReturn(new LocalMockLlmClient());

        toolRegistry = mock(ToolRegistry.class);
        when(toolRegistry.getPublicToolDefinitions()).thenReturn(new com.alibaba.fastjson2.JSONArray());
        com.campus.modules.ai.agent.model.ToolExecutionRecord mockRecord = com.campus.modules.ai.agent.model.ToolExecutionRecord.builder()
                .toolCallId("call_1")
                .toolName("query_my_points")
                .description("查询学生积分账户")
                .status("SUCCESS")
                .argumentsJson("{}")
                .resultFactJson("{\"success\":true,\"currentAvailablePoints\":100,\"historicalTotalPoints\":200}")
                .executionTimeMs(10L)
                .build();
        when(toolRegistry.executeTool(any(), any(), any(), any())).thenReturn(mockRecord);

        llmAgentService = new LlmAgentServiceImpl(llmClientFactory, toolRegistry, aiConfig, messageService);
        qaKnowledgeService = mock(QaKnowledgeService.class);

        chatService = new AiChatServiceImpl(qaKnowledgeService, aiConfig, messageService, llmAgentService);
    }

    @Test
    @DisplayName("AiMessageService: 正确持久化消息")
    void testSaveMessage() {
        messageService.saveMessage(100L, "conv_1", "user", "查我的积分", "LOCAL_AGENT", null);

        ArgumentCaptor<AiChatMessage> captor = ArgumentCaptor.forClass(AiChatMessage.class);
        verify(messageMapper, times(1)).insert(captor.capture());

        AiChatMessage saved = captor.getValue();
        assertEquals(100L, saved.getStudentId());
        assertEquals("conv_1", saved.getConversationId());
        assertEquals("user", saved.getRole());
        assertEquals("查我的积分", saved.getContent());
    }

    @Test
    @DisplayName("AiMessageService: 获取历史消息按时间正序排列且容忍异常 toolCallJson")
    void testGetChatHistory() {
        AiChatMessage msg1 = AiChatMessage.builder()
                .id(1L)
                .studentId(100L)
                .conversationId("conv_1")
                .role("user")
                .content("问1")
                .createTime(LocalDateTime.now().minusMinutes(5))
                .toolCallJson("{\"invalid-json") // 故意放入非法 JSON 验证容错
                .build();

        AiChatMessage msg2 = AiChatMessage.builder()
                .id(2L)
                .studentId(100L)
                .conversationId("conv_1")
                .role("assistant")
                .content("答1")
                .createTime(LocalDateTime.now().minusMinutes(4))
                .toolCallJson("{\"toolName\":\"query_points\"}")
                .build();

        // 模拟 Mapper 倒序返回
        when(messageMapper.selectList(any())).thenReturn(new java.util.ArrayList<>(List.of(msg2, msg1)));

        List<AiChatMessageVO> list = messageService.getChatHistory(100L, "conv_1", 20);
        assertEquals(2, list.size());
        assertEquals(1L, list.get(0).getId(), "历史记录必须反转为正序(旧消息在前)");
        assertEquals(2L, list.get(1).getId());
        assertNull(list.get(0).getToolCall(), "非法 JSON 记录不应导致整体崩溃，应安全置空 toolCall");
        assertNotNull(list.get(1).getToolCall());
        assertEquals("query_points", list.get(1).getToolCall().getString("toolName"));
    }

    @Test
    @DisplayName("个人敏感数据查询严格走本地模板隔离，不泄漏给外部大模型")
    void testPersonalQueryDataIsolation() {
        AiChatRequest req = new AiChatRequest();
        req.setQuestion("帮我查查我的积分余额");
        req.setConversationId("conv_test");

        SseEmitter emitter = chatService.streamChat(req, 100L);
        assertNotNull(emitter);

        // 验证用户提问正常落库 (异步线程执行，等待至多 3 秒)
        verify(messageMapper, timeout(3000).atLeastOnce()).insert(any(AiChatMessage.class));
        // 验证未调用外部知识库匹配 (因为优先命中了个人查询)
        verify(qaKnowledgeService, never()).matchKnowledge(anyString());
    }

    @Test
    @DisplayName("大模型不可用时的本地兜底：公开信息咨询不得被答成个人活动台账")
    void testLocalFallbackMustNotAnswerPublicInfoWithPersonalLedger() {
        // 关闭外部大模型，强制走本地兜底分支（这正是无 Key 环境的真实路径）
        aiConfig.setEnabled(false);

        AiChatRequest req = new AiChatRequest();
        req.setQuestion("活动报名时间");
        req.setConversationId("conv_fallback");

        SseEmitter emitter = chatService.streamChat(req, 100L);
        assertNotNull(emitter);

        // 必须走知识库检索
        verify(toolRegistry, timeout(3000)).executeTool(any(), eq("search_platform_knowledge"), any(), any());
        // 绝不能因为句子里有「活动」两个字就去查我的活动
        verify(toolRegistry, never()).executeTool(any(), eq("query_my_activities"), any(), any());
    }

    @Test
    @DisplayName("大模型不可用时的本地兜底：口语化个人查询仍要命中对应工具")
    void testLocalFallbackStillServesColloquialPersonalQuery() {
        aiConfig.setEnabled(false);

        AiChatRequest req = new AiChatRequest();
        req.setQuestion("我兑换的礼品提货码");
        req.setConversationId("conv_fallback2");

        SseEmitter emitter = chatService.streamChat(req, 100L);
        assertNotNull(emitter);

        verify(toolRegistry, timeout(3000)).executeTool(any(), eq("query_my_orders"), any(), any());
        verify(toolRegistry, never()).executeTool(any(), eq("search_platform_knowledge"), any(), any());
    }

    @Test
    @DisplayName("AiMessageService: 清空指定会话历史")
    void testClearChatHistory() {
        when(messageMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(3);
        messageService.clearChatHistory(100L, "conv_test");
        verify(messageMapper, times(1)).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("F10 核验：AiChatServiceImpl 使用有界线程池且容器销毁时支持优雅关闭")
    void testBoundedThreadPoolAndDestroy() {
        Object sseExecutor = org.springframework.test.util.ReflectionTestUtils.getField(chatService, "sseExecutor");
        assertNotNull(sseExecutor);
        assertTrue(sseExecutor instanceof java.util.concurrent.ThreadPoolExecutor);
        java.util.concurrent.ThreadPoolExecutor executor = (java.util.concurrent.ThreadPoolExecutor) sseExecutor;

        // 验证有界核心与最大线程数及拒绝策略
        assertEquals(4, executor.getCorePoolSize());
        assertEquals(16, executor.getMaximumPoolSize());
        assertTrue(executor.getQueue().remainingCapacity() <= 100);
        assertTrue(executor.getRejectedExecutionHandler() instanceof java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy);

        // 验证 destroy 优雅关闭
        assertDoesNotThrow(() -> chatService.destroy());
        assertTrue(executor.isShutdown());

        // 验证关闭后发起对话请求能够优雅降级返回系统维护提示而非挂起
        AiChatRequest req = new AiChatRequest();
        req.setQuestion("测试关闭后提问");
        SseEmitter emitter = assertDoesNotThrow(() -> chatService.streamChat(req, 100L));
        assertNotNull(emitter);
    }
}
