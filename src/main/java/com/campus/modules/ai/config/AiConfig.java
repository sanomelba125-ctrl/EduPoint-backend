package com.campus.modules.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 客服与大模型客户端配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.llm")
public class AiConfig {

    /** 是否启用外部大语言模型API调用 (未启用或未配 Key 时自动优雅使用本地智能规则+流式Mock) */
    private boolean enabled = false;

    /** OpenAI 兼容协议的基础地址，例如: https://api.deepseek.com/v1 */
    private String baseUrl = "https://api.deepseek.com/v1";

    /** 模型 API 密钥 */
    private String apiKey = "";

    /** 调用模型代号，例如 deepseek-chat / qwen-plus */
    private String model = "deepseek-chat";

    /** 超时时间 (秒) */
    private int timeoutSeconds = 30;
}
