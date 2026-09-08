package com.campus.common.websocket;

import com.alibaba.fastjson2.JSON;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket 服务端
 * 路径格式：/ws/{userType}/{userId}
 * userType: admin / student
 */
@ServerEndpoint("/ws/{userType}/{userId}")
@Component
@Slf4j
public class WebSocketServer {

    // 静态变量，用来记录当前在线连接数
    private static AtomicInteger onlineCount = new AtomicInteger(0);

    // 线程安全 Map，用来存放每个客户端对应的 WebSocketServer 对象
    private static ConcurrentHashMap<String, WebSocketServer> webSocketMap = new ConcurrentHashMap<>();

    // 与某个客户端的连接会话
    private Session session;

    // 接收 userId
    private String userId;
    
    // 接收 userType
    private String userType;

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("userType") String userType, @PathParam("userId") String userId) {
        this.session = session;
        this.userId = userId;
        this.userType = userType;
        
        String key = buildKey(userType, userId);
        
        if (webSocketMap.containsKey(key)) {
            webSocketMap.remove(key);
            webSocketMap.put(key, this);
        } else {
            webSocketMap.put(key, this);
            addOnlineCount();
        }
        
        log.info("用户连接: {}, 当前在线人数: {}", key, getOnlineCount());
        
        try {
            sendMessage("连接成功");
        } catch (IOException e) {
            log.error("用户: {} 网络异常", key);
        }
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose() {
        String key = buildKey(userType, userId);
        if (webSocketMap.containsKey(key)) {
            webSocketMap.remove(key);
            subOnlineCount();
        }
        log.info("用户退出: {}, 当前在线人数: {}", key, getOnlineCount());
    }

    /**
     * 收到客户端消息后调用的方法
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        log.info("收到用户消息: {}, 报文: {}", userId, message);
    }

    /**
     * 发生错误时调用
     */
    @OnError
    public void onError(Session session, Throwable error) {
        log.error("用户错误: {}, 原因: {}", this.userId, error.getMessage(), error);
    }

    /**
     * 实现服务器主动推送文本
     */
    public void sendMessage(String message) throws IOException {
        this.session.getBasicRemote().sendText(message);
    }
    
    /**
     * 发送自定义对象
     */
    public void sendMessage(Object object) throws IOException {
        this.session.getBasicRemote().sendText(JSON.toJSONString(object));
    }

    /**
     * 向指定用户发送消息
     * 
     * @param userType 用户类型 (admin/student)
     * @param userId   用户ID
     * @param message  消息内容
     */
    public static void sendInfo(String userType, String userId, String message) {
        String key = buildKey(userType, userId);
        log.info("发送消息到: {}, 报文: {}", key, message);
        if (webSocketMap.containsKey(key)) {
            try {
                webSocketMap.get(key).sendMessage(message);
            } catch (IOException e) {
                log.error("发送消息失败，用户: {}, 异常: {}", key, e.getMessage());
            }
        } else {
            log.warn("发送失败，用户: {} 不在线！", key);
        }
    }
    
    /**
     * 向指定用户类型的所有在线客户端广播消息
     */
    public static void broadcastToUserType(String userType, String message) {
        webSocketMap.forEach((key, server) -> {
            if (key.startsWith(userType + "_")) {
                try {
                    server.sendMessage(message);
                } catch (IOException e) {
                    log.error("广播消息失败，key: {}, error: {}", key, e.getMessage());
                }
            }
        });
    }

    /**
     * 构建存储的 Key
     */
    private static String buildKey(String userType, String userId) {
        return userType + "_" + userId;
    }

    public static synchronized int getOnlineCount() {
        return onlineCount.get();
    }

    public static synchronized void addOnlineCount() {
        WebSocketServer.onlineCount.incrementAndGet();
    }

    public static synchronized void subOnlineCount() {
        WebSocketServer.onlineCount.decrementAndGet();
    }
}