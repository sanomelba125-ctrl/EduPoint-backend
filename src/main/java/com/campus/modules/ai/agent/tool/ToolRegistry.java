package com.campus.modules.ai.agent.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.campus.modules.ai.agent.model.ToolExecutionRecord;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 工具注册中心 (Tool Registry)
 * 负责收集系统所有 AiTool 实现，生成 OpenAI 兼容的 Tools 描述，并进行路由与执行
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRegistry {

    private final List<AiTool> toolList;
    private final Map<String, AiTool> toolMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        if (toolList != null) {
            for (AiTool tool : toolList) {
                toolMap.put(tool.getName(), tool);
                log.info("【AI 工具注册中心】成功注册工具: [{}] - {}", tool.getName(), tool.getDescription());
            }
        }
    }

    /**
     * 获取全量工具的 OpenAI / SenseNova 兼容协议定义列表
     */
    public JSONArray getOpenAiToolDefinitions() {
        JSONArray array = new JSONArray();
        for (AiTool tool : toolMap.values()) {
            array.add(tool.getToolDefinition());
        }
        return array;
    }

    /**
     * 根据函数名获取对应工具
     */
    public AiTool getTool(String name) {
        return toolMap.get(name);
    }

    /**
     * 路由并执行指定工具，记录执行指标与轨迹
     *
     * @param toolCallId 大模型分配的调用 ID
     * @param toolName 工具名称
     * @param argumentsJson 入参 JSON 字符串
     * @param studentId 当前登录学生 ID (强制注入 Sa-Token 安全上下文，阻断越权 IDOR)
     * @return 工具执行轨迹记录实体
     */
    public ToolExecutionRecord executeTool(String toolCallId, String toolName, String argumentsJson, Long studentId) {
        long startTime = System.currentTimeMillis();
        AiTool tool = toolMap.get(toolName);

        if (tool == null) {
            log.warn("【AI 工具注册中心】未找到对应工具: {}", toolName);
            long cost = System.currentTimeMillis() - startTime;
            JSONObject errJson = new JSONObject();
            errJson.put("success", false);
            errJson.put("error", "未找到指定的工具能力: " + toolName);

            return ToolExecutionRecord.builder()
                    .toolCallId(toolCallId)
                    .toolName(toolName)
                    .description("未知工具")
                    .argumentsJson(argumentsJson)
                    .resultFactJson(errJson.toJSONString())
                    .executionTimeMs(cost)
                    .status("FAILED")
                    .errorMessage("工具未注册")
                    .build();
        }

        JSONObject args = new JSONObject();
        try {
            if (argumentsJson != null && !argumentsJson.trim().isEmpty()) {
                args = JSON.parseObject(argumentsJson);
            }
        } catch (Exception e) {
            log.warn("解析工具入参 JSON 失败: {}, error={}", argumentsJson, e.getMessage());
        }

        try {
            log.info("【AI 工具执行】触发工具: [{}] (CallId: {}), 入参: {}, 绑定学生ID: {}",
                    toolName, toolCallId, argumentsJson, studentId);

            String factJson = tool.execute(studentId, args);
            long cost = System.currentTimeMillis() - startTime;

            log.info("【AI 工具执行完成】工具: [{}] 耗时: {} ms, 事实数据: {}",
                    toolName, cost, factJson.length() > 200 ? factJson.substring(0, 200) + "..." : factJson);

            return ToolExecutionRecord.builder()
                    .toolCallId(toolCallId)
                    .toolName(toolName)
                    .description(tool.getDescription())
                    .argumentsJson(argumentsJson)
                    .resultFactJson(factJson)
                    .executionTimeMs(cost)
                    .status("SUCCESS")
                    .build();
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - startTime;
            log.error("【AI 工具执行异常】工具: [{}] 失败: {}", toolName, e.getMessage(), e);

            JSONObject errJson = new JSONObject();
            errJson.put("success", false);
            errJson.put("error", "工具执行异常: " + e.getMessage());

            return ToolExecutionRecord.builder()
                    .toolCallId(toolCallId)
                    .toolName(toolName)
                    .description(tool.getDescription())
                    .argumentsJson(argumentsJson)
                    .resultFactJson(errJson.toJSONString())
                    .executionTimeMs(cost)
                    .status("FAILED")
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
}
