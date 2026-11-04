package com.campus.modules.ai.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.campus.modules.ai.config.AiConfig;
import com.campus.modules.ai.dto.AiChatRequest;
import com.campus.modules.ai.service.AiChatService;
import com.campus.modules.ai.service.AiMessageService;
import com.campus.modules.ai.service.LlmAgentService;
import com.campus.modules.ai.service.QaKnowledgeService;
import com.campus.modules.ai.util.DataDesensitizer;
import com.campus.modules.ai.vo.AiChatMessageVO;
import com.campus.modules.system.entity.QaKnowledge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.campus.common.util.TraceUtil;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 客服流式对话业务协调器实现
 * 职责：会话生命周期管理、敏感词脱敏、意图路由、知识库直配，调度 LlmAgentService 与 AiMessageService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService, DisposableBean {

    private final QaKnowledgeService qaKnowledgeService;
    private final AiConfig aiConfig;
    private final AiMessageService aiMessageService;
    private final LlmAgentService llmAgentService;

    /** 用于流式推送的有界守护线程池，防止突发流量耗尽系统线程与内存 (F10 加固) */
    private final ThreadPoolExecutor sseExecutor = new ThreadPoolExecutor(
            4,
            16,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "ai-chat-sse-" + threadNumber.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.AbortPolicy()
    );

    @Override
    @PreDestroy
    public void destroy() {
        log.info("正在优雅关闭 AI 对话流线程池...");
        sseExecutor.shutdown();
        try {
            if (!sseExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                sseExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            sseExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public SseEmitter streamChat(AiChatRequest request, Long studentId) {
        SseEmitter emitter = new SseEmitter(120_000L);

        emitter.onCompletion(() -> log.debug("SSE 对话连接正常关闭"));
        emitter.onTimeout(() -> {
            log.warn("SSE 对话连接超时");
            emitter.complete();
        });
        emitter.onError(e -> log.error("SSE 对话发生异常: {}", e.getMessage()));

        if (sseExecutor.isShutdown()) {
            log.warn("AI 对话流线程池已关闭，拒绝新的对话请求");
            sendChunk(emitter, "\n\n【系统提示】AI 助手服务正在维护，请稍后重试。", true, "SYSTEM", null);
            sendDone(emitter);
            return emitter;
        }

        try {
            sseExecutor.submit(TraceUtil.wrap(() -> {
                try {
                    processChatStream(request, studentId, emitter);
                } catch (Exception e) {
                    log.error("处理 AI 问答流时发生未捕获异常", e);
                    sendChunk(emitter, "\n\n【系统提示】AI 响应过程遇到异常，请稍后重试。", true, "ERROR", null);
                    sendDone(emitter);
                }
            }));
        } catch (RejectedExecutionException e) {
            log.warn("AI 对话流线程池已满或被拒绝: {}", e.getMessage());
            sendChunk(emitter, "\n\n【系统提示】AI 助手当前访问人数较多，请稍后重试。", true, "BUSY", null);
            sendDone(emitter);
        }

        return emitter;
    }

    private void processChatStream(AiChatRequest request, Long studentId, SseEmitter emitter) {
        String rawQuestion = request != null ? request.getQuestion() : "";
        String conversationId = (request != null && StringUtils.hasText(request.getConversationId()))
                ? request.getConversationId().trim()
                : "default";

        if (!StringUtils.hasText(rawQuestion)) {
            sendChunk(emitter, "您好！我是校园活动小助手\"小智\"，请问有什么可以帮您的吗？", true, "SYSTEM", null);
            sendDone(emitter);
            return;
        }

        // 1. 敏感数据脱敏过滤器 (DataDesensitizer)
        String cleanQuestion = DataDesensitizer.desensitize(rawQuestion.trim());
        // 只记录脱敏后的文本：原文里可能含手机号、密码，日志同样属于数据外泄面
        log.info("【AI 对话】收到提问: [学生ID:{}, 会话:{}] 原文字符数:{}, 脱敏后: '{}'",
                studentId, conversationId, rawQuestion.length(), cleanQuestion);

        // 持久化用户提问 (role: user)
        aiMessageService.saveMessage(studentId, conversationId, "user", cleanQuestion, null, null);

        // 2. 个人查询优先走本地模板，严禁将个人隐私数据发送给外部大模型
        if (isPersonalQuery(cleanQuestion)) {
            llmAgentService.streamLocalAgentAnswer(emitter, cleanQuestion, studentId, conversationId);
            sendDone(emitter);
            return;
        }

        // 3. 检查是否为完全无需调用工具的强置信度高频官方标准问答
        QaKnowledge hit = qaKnowledgeService.matchKnowledge(cleanQuestion);
        if (hit != null) {
            qaKnowledgeService.incrementViewCount(hit.getId());
            streamKnowledgeAnswer(emitter, hit);
            sendDone(emitter);
            aiMessageService.saveMessage(studentId, conversationId, "assistant", "【官方知识库解答】\n\n" + hit.getAnswer(), "KNOWLEDGE_BASE", null);
            return;
        }

        // 4. 公开查询优先调度大模型 Function Calling 引擎
        if (aiConfig.isEnabled()) {
            boolean llmSuccess = llmAgentService.runLlmAgentLoop(cleanQuestion, studentId, conversationId, emitter);
            if (llmSuccess) {
                sendDone(emitter);
                return;
            }
            log.warn("外部大模型接口调用失败或网络不可达，自动无缝降级为本地智能工具引擎 (Local Agent)");
        }

        // 5. 本地智能工具引擎兜底 (Local Agent Fallback)
        llmAgentService.streamLocalAgentAnswer(emitter, cleanQuestion, studentId, conversationId);
        sendDone(emitter);
    }

    /**
     * 判断是否在问「我自己的」业务数据，决定走本地工具还是外部大模型。
     *
     * <p>判定规则已抽到 {@link com.campus.modules.ai.service.AiIntentClassifier}：
     * 旧实现是 {@code question.contains("我")}，会把「我怎么参加活动拿积分」误判成个人查询，
     * 又漏掉「查询积分流水」这种不带「我」的个人查询（导致模型回一句「请查看上方查询结果」而上方什么都没有）。
     * 规则必须能单测，所以不再内联在这里。</p>
     */
    private boolean isPersonalQuery(String question) {
        return com.campus.modules.ai.service.AiIntentClassifier.isPersonalQuery(question);
    }

    private void streamKnowledgeAnswer(SseEmitter emitter, QaKnowledge item) {
        String prefix = "【官方知识库解答】\n\n";
        sendChunk(emitter, prefix, false, "KNOWLEDGE_BASE", null);
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String answer = item.getAnswer();
        if (StringUtils.hasText(answer)) {
            int step = 6;
            for (int i = 0; i < answer.length(); i += step) {
                int end = Math.min(i + step, answer.length());
                String chunk = answer.substring(i, end);
                boolean isLast = (end == answer.length());
                sendChunk(emitter, chunk, isLast, "KNOWLEDGE_BASE", null);
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void sendChunk(SseEmitter emitter, String content, boolean finish, String source, JSONObject toolCall) {
        try {
            JSONObject data = new JSONObject();
            data.put("content", content);
            data.put("finish", finish);
            data.put("source", source);
            if (toolCall != null) {
                data.put("toolCall", toolCall);
            }
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(data.toJSONString()));
        } catch (Exception e) {
            log.debug("向客户端推送 SSE chunk 失败(可能客户端已断开): {}", e.getMessage());
        }
    }

    private void sendDone(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            emitter.complete();
        } catch (Exception e) {
            log.debug("完成 SSE 对话流失败: {}", e.getMessage());
        }
    }

    @Override
    public List<AiChatMessageVO> getChatHistory(Long studentId, String conversationId, int limit) {
        return aiMessageService.getChatHistory(studentId, conversationId, limit);
    }

    @Override
    public void clearChatHistory(Long studentId, String conversationId) {
        aiMessageService.clearChatHistory(studentId, conversationId);
    }

    @Override
    public void saveMessage(Long studentId, String conversationId, String role, String content, String source, String toolCallJson) {
        aiMessageService.saveMessage(studentId, conversationId, role, content, source, toolCallJson);
    }
}
