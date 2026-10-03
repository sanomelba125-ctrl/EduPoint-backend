package com.campus.modules.ai.agent.tool;

import com.alibaba.fastjson2.JSONObject;

/**
 * AI Agent 工具执行结果统一响应信封
 * 统一标准化输出格式：
 * - 成功：{"success": true, ...}
 * - 失败：{"success": false, "error": "..."}
 */
public final class AiToolResults {

    private AiToolResults() {
    }

    /**
     * 构建失败信封
     *
     * @param errorMessage 错误提示文案
     * @return 序列化后的 JSON 字符串
     */
    public static String error(String errorMessage) {
        JSONObject err = new JSONObject();
        err.put("success", false);
        err.put("error", errorMessage != null ? errorMessage : "未知执行错误");
        return err.toJSONString();
    }

    /**
     * 为业务返回体注入 success: true 字段并序列化
     *
     * @param payload 业务数据对象
     * @return 序列化后的 JSON 字符串
     */
    public static String ok(JSONObject payload) {
        if (payload == null) {
            payload = new JSONObject();
        }
        payload.put("success", true);
        return payload.toJSONString();
    }

    /**
     * 空成功信封
     *
     * @return 序列化后的 JSON 字符串
     */
    public static String ok() {
        JSONObject ok = new JSONObject();
        ok.put("success", true);
        return ok.toJSONString();
    }
}
