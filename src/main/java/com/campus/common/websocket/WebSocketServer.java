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
 * WebSocket 服务�? * 路径格式�?ws/{userType}/{userId}
 * userType: admin / student
 */
@ServerEndpoint("/ws/{userType}/{userId}")
@Component
@Slf4j
public class WebSocketServer {

    // 静态变量，用来记录当前在线连接数。应该把它设计成线程安全的�?    private static AtomicInteger onlineCount = new AtomicInteger(0);

    // concurrent包的线程安全Map，用来存放每个客户端对应的MyWebSocket对象�?    private static ConcurrentHashMap<String, WebSocketServer> webSocketMap = new ConcurrentHashMap<>();

    // 与某个客户端的连接会话，需要通过它来给客户端发送数�?    private Session session;

    // 接收userId
    private String userId;
    
    // 接收userType
    private String userType;

    /**
     * 连接建立成功调用的方�?     */
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
        
        log.info("用户连接:" + key + ",当前在线人数�?" + getOnlineCount());
        
        try {
            sendMessage("连接成功");
        } catch (IOException e) {
            log.error("用户:" + key + ",网络异常!!!!!!");
        }
    }

    /**
     * 连接关闭调用的方�?     */
    @OnClose
    public void onClose() {
        String key = buildKey(userType, userId);
        if (webSocketMap.containsKey(key)) {
            webSocketMap.remove(key);
            subOnlineCount();
        }
        log.info("用户退�?" + key + ",当前在线人数�?" + getOnlineCount());
    }

    /**
     * 收到客户端消息后调用的方�?     *
     * @param message 客户端发送过来的消息
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        log.info("收到用户消息:" + userId + ",报文:" + message);
        // 可以群发消息，这里暂不需�?    }

    /**
     * @param session
     * @param error
     */
    @OnError
    public void onError(Session session, Throwable error) {
        log.error("用户错误:" + this.userId + ",原因:" + error.getMessage());
        error.printStackTrace();
    }

    /**
     * 实现服务器主动推�?     */
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
     * 向指定用户发送消�?     * 
     * @param userType 用户类型 (admin/student)
     * @param userId   用户ID
     * @param message  消息内容
     */
    public static void sendInfo(String userType, String userId, String message) {
        String key = buildKey(userType, userId);
        log.info("发送消息到:" + key + "，报�?" + message);
        if (webSocketMap.containsKey(key)) {
            try {
                webSocketMap.get(key).sendMessage(message);
            } catch (IOException e) {
                log.error("发送消息失败，用户:" + key + ", 异常:" + e.getMessage());
            }
        } else {
            log.warn("发送失败，用户:" + key + " 不在线！");
        }
    }
    
    /**
     * 构建存储的Key
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
