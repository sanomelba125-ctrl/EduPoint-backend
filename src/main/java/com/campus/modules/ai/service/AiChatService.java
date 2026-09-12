package com.campus.modules.ai.service;

import com.campus.modules.ai.dto.AiChatRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
}
