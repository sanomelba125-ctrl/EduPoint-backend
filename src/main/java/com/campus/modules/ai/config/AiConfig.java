package com.campus.modules.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 客服与大模型客户端配置 (适配商汤日日新 SenseNova 6.8 Flash Lite 及 OpenAI 兼容大模型)
 * 对应《智能交互与凭证中心实施规划》任务一
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.llm")
public class AiConfig {

    /** 是否启用外部大语言模型API调用 (未启用或未配 Key 时自动优雅使用本地智能规则+流式Mock) */
    private boolean enabled = true;

    /** 指定提供商：sensenova / openai / deepseek / local */
    private String provider = "sensenova";

    /** 商汤 SenseNova / OpenAI 兼容协议的基础地址，例如: https://api.sensenova.cn/compatible-mode/v1 */
    private String baseUrl = "https://api.sensenova.cn/compatible-mode/v1";

    /** 模型 API 密钥 (商汤开放平台获取的 SENSENOVA_API_KEY) */
    private String apiKey = "";

    /** 调用模型代号，商汤轻量高效智能体模型: SenseNova-6.8-Flash-Lite */
    private String model = "SenseNova-6.8-Flash-Lite";

    /** 业务问答场景温度系数 (推荐 0.2，降低发散度以保障严谨度) */
    private Double temperature = 0.2;

    /** 超时时间 (秒) */
    private int timeoutSeconds = 60;

    /** 思考推理力度 (none: 关闭推理秒级极速响应，适合即时客服; low, medium, high) */
    private String reasoningEffort = "none";

    /** 单次响应最大输出 Token 数 */
    private Integer maxTokens = 4096;
}
