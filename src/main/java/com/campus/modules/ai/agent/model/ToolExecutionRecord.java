package com.campus.modules.ai.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Function Calling 工具执行轨迹记录实体 (用于 SSE 轨迹传输与管理端调试透视窗展示)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionRecord {

    /** 工具调用 ID (来自大模型 tool_call_id) */
    private String toolCallId;

    /** 调用的工具函数名称 (如 query_my_points) */
    private String toolName;

    /** 工具中文业务描述 */
    private String description;

    /** 模型下发的调用入参 (JSON 字符串) */
    private String argumentsJson;

    /** 后端服务执行后返回的业务事实结果 (JSON 字符串) */
    private String resultFactJson;

    /** 工具执行耗时 (毫秒) */
    private Long executionTimeMs;

    /** 执行状态: SUCCESS / FAILED */
    private String status;

    /** 异常信息 (若有) */
    private String errorMessage;
}
