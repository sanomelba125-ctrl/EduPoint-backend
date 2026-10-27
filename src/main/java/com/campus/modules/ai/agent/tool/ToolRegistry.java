package com.campus.modules.ai.agent.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.campus.modules.ai.agent.model.ToolExecutionRecord;
import com.campus.modules.ai.util.DataDesensitizer;
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
     * 获取仅公开工具的 OpenAI 兼容协议定义列表（仅公开工具可发送给外部大模型）
     * 个人工具（积分、订单等）仅在本地模板执行，不向外部大模型暴露
     */
    public JSONArray getPublicToolDefinitions() {
        JSONArray array = new JSONArray();
        for (AiTool tool : toolMap.values()) {
            if (tool.isPublic()) {
                array.add(tool.getToolDefinition());
            }
        }
        return array;
    }

    /**
     * 判断指定工具是否为公开工具
     */
    public boolean isPublicTool(String name) {
        AiTool tool = toolMap.get(name);
        return tool != null && tool.isPublic();
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
            String errJson = AiToolResults.error("未找到指定的工具能力: " + toolName);

            return ToolExecutionRecord.builder()
                    .toolCallId(toolCallId)
                    .toolName(toolName)
                    .description("未知工具")
                    .argumentsJson(argumentsJson)
                    .resultFactJson(errJson)
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
            log.warn("解析工具入参 JSON 失败: {}, error={}", safeLogText(argumentsJson), e.getMessage());
        }

        try {
            log.info("【AI 工具执行】触发工具: [{}] (CallId: {}), 入参(脱敏): {}, 绑定学生ID: {}",
                    toolName, toolCallId, safeLogText(argumentsJson), studentId);

            String factJson = tool.execute(studentId, args);
            long cost = System.currentTimeMillis() - startTime;

            // 事实数据可能含积分、订单、提货码等业务明细，日志只保留大小，不落内容
            log.info("【AI 工具执行完成】工具: [{}] 耗时: {} ms, 事实数据字符数: {}",
                    toolName, cost, factJson == null ? 0 : factJson.length());

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

            String errJson = AiToolResults.error("工具执行异常: " + e.getMessage());

            return ToolExecutionRecord.builder()
                    .toolCallId(toolCallId)
                    .toolName(toolName)
                    .description(tool.getDescription())
                    .argumentsJson(argumentsJson)
                    .resultFactJson(errJson)
                    .executionTimeMs(cost)
                    .status("FAILED")
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * 日志安全输出：先脱敏再截断，避免手机号、密码、订单号等原样落盘。
     */
    private String safeLogText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String masked = DataDesensitizer.desensitize(text);
        return masked.length() > 120 ? masked.substring(0, 120) + "..." : masked;
    }
}
