package com.campus.common.websocket;

import com.alibaba.fastjson2.JSON;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@ServerEndpoint("/ws/{userType}/{userId}")
@Component
public class WebSocketServer {
    private static final AtomicInteger onlineCount = new AtomicInteger();
    private static final ConcurrentHashMap<String, WebSocketServer> webSocketMap = new ConcurrentHashMap<>();
    private Session session;
    private String userId;
    private String userType;
    @OnOpen
    public void onOpen(Session session, @PathParam("userType") String userType, @PathParam("userId") String userId) {
        this.session = session; this.userType = userType; this.userId = userId;
        if (webSocketMap.put(buildKey(userType, userId), this) == null) onlineCount.incrementAndGet();
        try { sendMessage("连接成功"); } catch (IOException ignored) { }
    }
    @OnClose public void onClose() { if (webSocketMap.remove(buildKey(userType, userId)) != null) onlineCount.decrementAndGet(); }
    @OnMessage public void onMessage(String message, Session session) { }
    @OnError public void onError(Session session, Throwable error) { }
    public void sendMessage(String message) throws IOException { session.getBasicRemote().sendText(message); }
    public void sendMessage(Object object) throws IOException { sendMessage(JSON.toJSONString(object)); }
    public static void sendInfo(String type, String id, String message) { WebSocketServer ws = webSocketMap.get(buildKey(type, id)); if (ws != null) try { ws.sendMessage(message); } catch (IOException ignored) { } }
    private static String buildKey(String type, String id) { return type + "_" + id; }
    public static int getOnlineCount() { return onlineCount.get(); }
    public static void addOnlineCount() { onlineCount.incrementAndGet(); }
    public static void subOnlineCount() { onlineCount.decrementAndGet(); }
}
