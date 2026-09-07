package com.campus.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * WebSocket 配置�? * 开�?WebSocket 支持
 */
@Configuration
public class WebSocketConfig {

    /**
     * 自动注册使用�?@ServerEndpoint 注解声明�?WebSocket endpoint
     */
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}
