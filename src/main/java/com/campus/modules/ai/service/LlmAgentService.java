package com.campus.modules.ai.service;

import com.campus.modules.ai.agent.model.ToolExecutionRecord;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 大模型 Agent 调度与工具协同执行领域服务接口
 */
public interface LlmAgentService {

    /**
     * 执行大模型两轮握手 Agent 循环 (Function Calling Loop)
     *
     * @param cleanQuestion  脱敏后的用户提问
     * @param studentId      学生ID
     * @param conversationId 会话分组ID
     * @param emitter        SSE 发送器
     * @return true 表示成功处理并流式输出；false 表示大模型不可达需要触发本地兜底
     */
    boolean runLlmAgentLoop(String cleanQuestion, Long studentId, String conversationId, SseEmitter emitter);

    /**
     * 本地智能工具引擎执行与兜底 (Local Agent)
     *
     * @param emitter        SSE 发送器
     * @param question       用户提问
     * @param studentId      学生ID
     * @param conversationId 会话分组ID
     */
    void streamLocalAgentAnswer(SseEmitter emitter, String question, Long studentId, String conversationId);

    /**
     * 第二轮大模型生成超时或异常时的已查事实降级展示
     *
     * @param emitter SSE 发送器
     * @param records 已成功执行的工具事实记录
     */
    void streamToolResultsFallback(SseEmitter emitter, List<ToolExecutionRecord> records);
}
