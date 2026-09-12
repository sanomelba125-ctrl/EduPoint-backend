package com.campus.modules.ai.controller;

import com.campus.common.auth.AuthUtil;
import com.campus.common.result.Result;
import com.campus.modules.ai.dto.AiChatRequest;
import com.campus.modules.ai.service.AiChatService;
import com.campus.modules.ai.service.QaKnowledgeService;
import com.campus.modules.ai.vo.AiFaqVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AI 智能客服问答端点
 * 时序图组件: AiChatController
 * 对应设计: POST /ai/chat/stream SSE 流式协议响应
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;
    private final QaKnowledgeService qaKnowledgeService;

    /**
     * AI 对话流式接口 (POST 方式，支持携带长文本及会话标识)
     * POST /api/ai/chat/stream
     * Content-Type: application/json
     * Produces: text/event-stream;charset=UTF-8
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChatPost(@RequestBody AiChatRequest request) {
        Long studentId = AuthUtil.currentStudentIdOrNull();
        return aiChatService.streamChat(request, studentId);
    }

    /**
     * AI 对话流式接口 (GET 方式，兼容原生 EventSource 规范)
     * GET /api/ai/chat/stream?question=xxx
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChatGet(@RequestParam("question") String question) {
        Long studentId = AuthUtil.currentStudentIdOrNull();
        AiChatRequest request = new AiChatRequest();
        request.setQuestion(question);
        return aiChatService.streamChat(request, studentId);
    }

    /**
     * 获取推荐热门问答清单 (用于进入对话页时的快捷气泡展示)
     * GET /api/ai/faq/recommended
     */
    @GetMapping("/faq/recommended")
    public Result<List<AiFaqVO>> getRecommendedFaqs(@RequestParam(defaultValue = "6") int limit) {
        List<AiFaqVO> list = qaKnowledgeService.getRecommendedFaqs(limit);
        return Result.success(list);
    }
}
