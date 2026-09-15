package com.campus.modules.ai.service;

import com.campus.modules.ai.dto.AiChatRequest;
import com.campus.modules.ai.vo.AiChatMessageVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AI 智能问答对话服务接口
 */
public interface AiChatService {

    /**
     * 流式处理学生提问对话
     *
     * @param request 提问入参
     * @param studentId 当前登录学生 ID（未登录可为 null）
     * @return SseEmitter 流式事件推送发射器
     */
    SseEmitter streamChat(AiChatRequest request, Long studentId);

    /**
     * 获取 AI 历史对话记录
     *
     * @param studentId 学生 ID (可为 null)
     * @param conversationId 会话 ID (可选)
     * @param limit 返回条数上限 (默认 50)
     * @return 历史消息列表 (按时间正序排列)
     */
    List<AiChatMessageVO> getChatHistory(Long studentId, String conversationId, int limit);

    /**
     * 清空 AI 对话历史
     *
     * @param studentId 学生 ID (可为 null)
     * @param conversationId 会话 ID (可选)
     */
    void clearChatHistory(Long studentId, String conversationId);

    /**
     * 保存单条 AI 消息
     *
     * @param studentId 学生 ID
     * @param conversationId 会话 ID
     * @param role 角色 (user/assistant/system)
     * @param content 消息正文
     * @param source 引擎来源
     * @param toolCallJson 工具调用 JSON
     */
    void saveMessage(Long studentId, String conversationId, String role, String content, String source, String toolCallJson);
}
