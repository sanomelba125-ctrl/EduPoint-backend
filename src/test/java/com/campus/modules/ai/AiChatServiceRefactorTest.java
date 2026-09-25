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
    @DisplayName("AiMessageService: 清空指定会话历史")
    void testClearChatHistory() {
        when(messageMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(3);
        messageService.clearChatHistory(100L, "conv_test");
        verify(messageMapper, times(1)).delete(any(LambdaQueryWrapper.class));
    }
}
