package com.campus.common.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.UUID;

/**
 * WebSocket 集群跨节点广播中继桥接器
 * 对应 P5-8a: 解决多实例水平扩展时跨节点推送失效、用户会话在不同节点间消息静默丢失问题
 */
@Slf4j
@Component
public class WebSocketClusterBridge {

    public static final String TOPIC_NAME = "websocket:cluster:broadcast";
    private static final String NODE_ID = UUID.randomUUID().toString();
    private static WebSocketClusterBridge INSTANCE;

    @Autowired(required = false)
    private RedissonClient redissonClient;

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClusterMessage implements Serializable {
        private String originNodeId;
        private String targetType; // USER or BROADCAST
        private String userType;
        private String userId;
        private String message;
    }

    @PostConstruct
    public void init() {
        INSTANCE = this;
        if (redissonClient != null) {
            try {
                RTopic topic = redissonClient.getTopic(TOPIC_NAME);
                topic.addListener(ClusterMessage.class, (channel, msg) -> {
                    if (msg == null) return;
                    // 过滤本节点发送的消息，防止本节点重复消费
                    if (NODE_ID.equals(msg.getOriginNodeId())) {
                        return;
                    }
                    log.debug("收到跨节点 WebSocket 广播: originNodeId={}, targetType={}, userType={}, userId={}",
                            msg.getOriginNodeId(), msg.getTargetType(), msg.getUserType(), msg.getUserId());
                    if ("USER".equalsIgnoreCase(msg.getTargetType())) {
                        WebSocketServer.deliverLocal(msg.getUserType(), msg.getUserId(), msg.getMessage());
                    } else if ("BROADCAST".equalsIgnoreCase(msg.getTargetType())) {
                        WebSocketServer.deliverLocalBroadcast(msg.getUserType(), msg.getMessage());
                    }
                });
                log.info("WebSocket 集群广播桥接已启动: nodeId={}, topic={}", NODE_ID, TOPIC_NAME);
            } catch (Exception e) {
                log.warn("初始化 WebSocket 集群广播监听异常，降级为单机模式: {}", e.getMessage());
            }
        } else {
            log.info("未检测到 RedissonClient，WebSocket 运行于单机模式");
        }
    }

    public static void publishUserMessage(String userType, String userId, String message) {
        if (INSTANCE != null && INSTANCE.redissonClient != null) {
            try {
                ClusterMessage msg = ClusterMessage.builder()
                        .originNodeId(NODE_ID)
                        .targetType("USER")
                        .userType(userType)
                        .userId(userId)
                        .message(message)
                        .build();
                INSTANCE.redissonClient.getTopic(TOPIC_NAME).publish(msg);
            } catch (Exception e) {
                log.warn("发布跨节点 WebSocket 消息异常: userType={}, userId={}, error={}", userType, userId, e.getMessage());
            }
        }
    }

    public static void publishBroadcastMessage(String userType, String message) {
        if (INSTANCE != null && INSTANCE.redissonClient != null) {
            try {
                ClusterMessage msg = ClusterMessage.builder()
                        .originNodeId(NODE_ID)
                        .targetType("BROADCAST")
                        .userType(userType)
                        .message(message)
                        .build();
                INSTANCE.redissonClient.getTopic(TOPIC_NAME).publish(msg);
            } catch (Exception e) {
                log.warn("发布跨节点 WebSocket 广播异常: userType={}, error={}", userType, e.getMessage());
            }
        }
    }

    public static String getNodeId() {
        return NODE_ID;
    }
}
