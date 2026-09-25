package com.campus.modules.ai.client;

import com.campus.modules.ai.config.AiConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大模型客户端抽象工厂 (Abstract Factory Pattern)
 * 统一管理各厂商实现，提供主备切换、降级路由与离线 Fallback 机制
 */
@Slf4j
@Component
public class LlmClientFactory {

    private final AiConfig aiConfig;
    private final Map<String, LlmClient> clientMap = new ConcurrentHashMap<>();

    @Autowired
    public LlmClientFactory(AiConfig aiConfig, List<LlmClient> clients) {
        this.aiConfig = aiConfig;
        if (clients != null) {
            for (LlmClient client : clients) {
                if (client != null && client.getProvider() != null) {
                    clientMap.put(client.getProvider().toLowerCase().trim(), client);
                }
            }
        }
        log.info("【LlmClientFactory】已初始化，注册客户端提供商列表: {}", clientMap.keySet());
    }

    /**
     * 根据提供商名称获取客户端
     */
    public Optional<LlmClient> getClient(String provider) {
        if (!StringUtils.hasText(provider)) {
            return Optional.empty();
        }
        return Optional.ofNullable(clientMap.get(provider.toLowerCase().trim()));
    }

    /**
     * 获取首选主大模型客户端 (按 aiConfig 配置优先获取)
     * 若未配置有效密钥或不可用，自动按备选降级路由
     */
    public LlmClient getPrimaryClient() {
        String configuredProvider = aiConfig != null && StringUtils.hasText(aiConfig.getProvider())
                ? aiConfig.getProvider().toLowerCase().trim()
                : "sensenova";

        LlmClient primary = clientMap.get(configuredProvider);
        if (primary != null && primary.isAvailable()) {
            return primary;
        }

        // 备选降级顺序: sensenova -> qwen -> deepseek -> local
        log.warn("主模型客户端 [{}] 不可用或未配置密钥，启动多级模型降级路由...", configuredProvider);
        for (String candidate : List.of("sensenova", "qwen", "deepseek")) {
            LlmClient fallback = clientMap.get(candidate);
            if (fallback != null && fallback.isAvailable()) {
                log.info("成功切换至备用大模型提供商: [{}]", candidate);
                return fallback;
            }
        }

        // 终极保底: 本地智能 Mock 客户端
        log.warn("所有外部大模型均不可用，降级为本地离线智能客户端 [local]");
        return getFallbackClient();
    }

    /**
     * 获取离线兜底客户端 (LocalMock)
     */
    public LlmClient getFallbackClient() {
        LlmClient local = clientMap.get("local");
        if (local != null) {
            return local;
        }
        // 如果未注入 local bean，兜底返回任意可用客户端
        return clientMap.values().stream()
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("无任何可用的 LlmClient 注册"));
    }

    /**
     * 获取当前所有已注册的提供商名称
     */
    public Set<String> getRegisteredProviders() {
        return Collections.unmodifiableSet(clientMap.keySet());
    }
}
