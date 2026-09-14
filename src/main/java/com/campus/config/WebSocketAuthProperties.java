package com.campus.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * WebSocket 握手鉴权配置
 *
 * <p>JSR-356 的 {@code ServerEndpointConfig.Configurator} 由容器反射实例化，无法使用依赖注入，
 * 因此这里在启动时把配置实例挂到静态引用上，供 {@code WebSocketAuthConfigurator} 读取。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "websocket.auth")
public class WebSocketAuthProperties {

    /** Origin 白名单，配置单个 {@code *} 表示放行所有来源（仅建议本地开发使用） */
    private List<String> allowedOrigins = new ArrayList<>(List.of(
            "http://localhost:3000",
            "http://localhost:3200",
            "http://localhost:5173",
            "http://127.0.0.1:3000",
            "http://127.0.0.1:3200",
            "http://127.0.0.1:5173"
    ));

    /** 是否放行不带 Origin 请求头的非浏览器客户端（微信小程序原生连接、部分真机调试不会携带 Origin） */
    private boolean allowMissingOrigin = true;

    private static volatile WebSocketAuthProperties instance;

    /** 供无法依赖注入的 WebSocket 配置器读取当前配置 */
    public static WebSocketAuthProperties current() {
        return instance;
    }

    @PostConstruct
    void register() {
        instance = this;
    }
}
