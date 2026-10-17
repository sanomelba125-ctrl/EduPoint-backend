package com.campus.modules.ai.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.annotation.RateLimit;
import com.campus.common.auth.AuthUtil;
import com.campus.common.result.Result;
import com.campus.modules.ai.dto.AiChatRequest;
import com.campus.modules.ai.service.AiChatService;
import com.campus.modules.ai.service.QaKnowledgeService;
import com.campus.modules.ai.vo.AiChatMessageVO;
import com.campus.modules.ai.vo.AiFaqVO;
import com.campus.modules.student.entity.Student;
import com.campus.modules.student.mapper.StudentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AI 智能客服问答端点
 * 时序图组件: AiChatController
 * 对应设计: POST /ai/chat/stream SSE 流式协议响应，支持历史查询与清空
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;
    private final QaKnowledgeService qaKnowledgeService;
    private final StudentMapper studentMapper;

    /**
     * AI 对话流式接口 (POST 方式，支持携带长文本及会话标识)
     * POST /api/ai/chat/stream
     * Content-Type: application/json
     * Produces: text/event-stream;charset=UTF-8
     */
    @SaCheckLogin
    @RateLimit(key = "rate_limit:ai:stream:user", count = 15, windowMs = 60000L, limitType = RateLimit.LimitType.USER, message = "AI 助手正在深度思考中，请勿频繁发送消息！")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChatPost(@RequestBody AiChatRequest request) {
        Long studentId = resolveStudentId(request);
        return aiChatService.streamChat(request, studentId);
    }

    /**
     * AI 对话流式接口 (GET 方式，兼容原生 EventSource 规范)
     * GET /api/ai/chat/stream?question=xxx
     */
    @SaCheckLogin
    @RateLimit(key = "rate_limit:ai:stream:user", count = 15, windowMs = 60000L, limitType = RateLimit.LimitType.USER, message = "AI 助手正在深度思考中，请勿频繁发送消息！")
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChatGet(@RequestParam("question") String question) {
        Long studentId = AuthUtil.currentStudentIdOrNull();
        AiChatRequest request = new AiChatRequest();
        request.setQuestion(question);
        return aiChatService.streamChat(request, studentId);
    }

    /**
     * 获取 AI 历史对话消息列表
     * GET /api/ai/chat/history
     */
    @SaCheckLogin
    @GetMapping("/chat/history")
    public Result<List<AiChatMessageVO>> getChatHistory(
            @RequestParam(value = "conversationId", required = false) String conversationId,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @RequestParam(value = "simulateStudentNo", required = false) String simulateStudentNo) {
        AiChatRequest req = new AiChatRequest();
        req.setConversationId(conversationId);
        req.setSimulateStudentNo(simulateStudentNo);
        Long studentId = resolveStudentId(req);
        List<AiChatMessageVO> history = aiChatService.getChatHistory(studentId, conversationId, limit);
        return Result.success(history);
    }

    /**
     * 清空 AI 对话历史消息
     * DELETE /api/ai/chat/history
     */
    @SaCheckLogin
    @DeleteMapping("/chat/history")
    public Result<Void> clearChatHistory(
            @RequestParam(value = "conversationId", required = false) String conversationId,
            @RequestParam(value = "simulateStudentNo", required = false) String simulateStudentNo) {
        AiChatRequest req = new AiChatRequest();
        req.setConversationId(conversationId);
        req.setSimulateStudentNo(simulateStudentNo);
        Long studentId = resolveStudentId(req);
        aiChatService.clearChatHistory(studentId, conversationId);
        return Result.success();
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

    /**
     * 安全解析当前学生 ID (IDOR 安全防护):
     * 1. 若当前登录身份为学生，严格取 token 关联的 studentId，阻断篡改。
     * 2. 若当前登录身份为管理员，允许在真机模拟器中指定 simulateStudentId 或 simulateStudentNo。
     * 3. 若无登录状态，则为匿名游客 (null)。
     */
    private Long resolveStudentId(AiChatRequest request) {
        Long authenticatedStudentId = AuthUtil.currentStudentIdOrNull();
        if (authenticatedStudentId != null) {
            return authenticatedStudentId;
        }

        // 仅已认证的管理员身份允许在调试模拟器中指定学生身份，杜绝未登录用户越权查询
        Long adminId = AuthUtil.currentAdminIdOrNull();
        if (adminId != null && request != null) {
            if (request.getSimulateStudentId() != null && request.getSimulateStudentId() > 0) {
                return request.getSimulateStudentId();
            }
            if (StringUtils.hasText(request.getSimulateStudentNo())) {
                Student s = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                        .eq(Student::getStudentNo, request.getSimulateStudentNo().trim())
                        .eq(Student::getDeleted, 0));
                if (s != null) {
                    return s.getId();
                }
            }
        }

        return null;
    }
}
