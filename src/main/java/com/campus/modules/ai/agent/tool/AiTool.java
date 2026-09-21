package com.campus.modules.ai.agent.tool;

import com.alibaba.fastjson2.JSONObject;

/**
 * AI 工具契约接口 (遵循 OpenAI Tools / Function Calling 规范)
 */
public interface AiTool {

    /**
     * 工具全局唯一函数名称
     */
    String getName();

    /**
     * 工具功能说明 (供大模型意图判定与路由参考)
     */
    String getDescription();

    /**
     * 是否为公开工具（公开工具可通过 Tool Calling 发送给外部大模型；个人工具仅在本地执行）
     */
    boolean isPublic();

    /**
     * 获取符合 OpenAI / SenseNova 协议的 Tools JSON Schema 定义
     */
    JSONObject getToolDefinition();

    /**
     * 执行业务工具调用
     *
     * @param studentId 当前登录学生 ID (强制注入 Sa-Token 安全上下文，阻断越权 IDOR)
     * @param arguments 大模型决策下发的 JSON 入参
     * @return 业务事实 JSON 字符串 (Fact JSON)
     */
    String execute(Long studentId, JSONObject arguments);
}
