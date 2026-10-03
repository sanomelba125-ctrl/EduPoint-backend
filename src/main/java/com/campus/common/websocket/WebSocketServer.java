package com.campus.common.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
 *
 * <p>握手阶段由 {@link WebSocketAuthConfigurator} 完成 Origin 白名单、token 有效性、路径身份一致性三重校验，
 * 校验不通过则握手直接失败，因此 {@link #onOpen} 可以信任路径参数。</p>
 *
 * <p><b>高可用架构改造（P5-8）：</b>
 * 1. P5-8b: 重连原子清理旧会话，杜绝旧 Session close 时因同名 Key 误删新会话及在线数变负；
 * 2. P5-8c: 解决并发写同一 Session 的 {@code IllegalStateException}，序列化全面统一使用 Jackson；
 * 3. P5-8a: 对接 {@link WebSocketClusterBridge} Redis Pub/Sub 广播通道，保障多实例水平扩展时跨节点推送触达。</p>
 */
@ServerEndpoint(
        value = WebSocketConstants.ENDPOINT_PATH,
        subprotocols = {WebSocketConstants.PROTOCOL_MARKER},
        configurator = WebSocketAuthConfigurator.class
)
@Component
@Slf4j
public class WebSocketServer {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    // 静态变量，用来记录当前在线连接数
    private static final AtomicInteger onlineCount = new AtomicInteger(0);

    // 线程安全 Map，用来存放每个客户端对应的 WebSocketServer 对象
    private static final ConcurrentHashMap<String, WebSocketServer> webSocketMap = new ConcurrentHashMap<>();

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

        // P5-8b: 原子替换 Map 中的会话；如果存在旧连接，立即主动显式关闭，防止僵尸连接残留
        WebSocketServer old = webSocketMap.put(key, this);
        if (old != null && old.session != null && old.session.isOpen()) {
            try {
                old.session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "Replaced by new connection"));
                log.info("用户重连，已主动关闭旧会话: key={}", key);
            } catch (Exception e) {
                log.warn("关闭旧 WebSocket 连接异常: key={}, error={}", key, e.getMessage());
            }
        } else if (old == null) {
            addOnlineCount();
        }

        log.info("用户连接建立: {}, 当前在线人数: {}", key, getOnlineCount());

        try {
            sendMessage("连接成功");
        } catch (IOException e) {
            log.error("用户: {} 发送连接成功消息异常: {}", key, e.getMessage());
        }
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose() {
        String key = buildKey(userType, userId);
        // P5-8b: 必须使用 ConcurrentHashMap 的双参数原子 remove(key, value)，
        // 确保只有当前断开的实例与 Map 中存储的完全一致时才移除并扣减人数，防止重连并发竞态误删已建立的新 Session
        if (webSocketMap.remove(key, this)) {
            subOnlineCount();
            log.info("用户正常退出连接并释放会话: {}, 当前在线人数: {}", key, getOnlineCount());
        } else {
            log.info("用户旧连接已断开(已被新重连会话替代，跳过移除与计数扣减): {}, 当前在线人数: {}", key, getOnlineCount());
        }
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
     * P5-8c: 加同步锁，彻底消除并发写同一 Session 时底层抛出的 IllegalStateException
     */
    public void sendMessage(String message) throws IOException {
        if (this.session != null && this.session.isOpen()) {
            synchronized (this.session) {
                this.session.getBasicRemote().sendText(message);
            }
        }
    }

    /**
     * 发送自定义对象
     * P5-8c: 序列化全面统一使用 Jackson，保障与 HTTP 接口端时间格式一致性
     */
    public void sendMessage(Object object) throws IOException {
        if (object == null) return;
        String text = (object instanceof String s) ? s : OBJECT_MAPPER.writeValueAsString(object);
        sendMessage(text);
    }

    /**
     * 向指定用户发送消息（集群模式：本机优先直接投递 + 跨节点 Redis 广播发布）
     *
     * @param userType 用户类型 (admin/student)
     * @param userId   用户ID
     * @param message  消息内容
     */
    public static void sendInfo(String userType, String userId, String message) {
        String key = buildKey(userType, userId);
        log.info("发送消息到: {}, 报文: {}", key, message);
        boolean deliveredLocally = deliverLocal(userType, userId, message);
        if (!deliveredLocally) {
            log.info("本地未找到活跃连接，广播至集群其他节点尝试投递: key={}", key);
        }
        WebSocketClusterBridge.publishUserMessage(userType, userId, message);
    }

    /**
     * 向指定用户类型的所有在线客户端广播消息（集群模式：本机广播 + 跨节点集群广播）
     */
    public static void broadcastToUserType(String userType, String message) {
        deliverLocalBroadcast(userType, message);
        WebSocketClusterBridge.publishBroadcastMessage(userType, message);
    }

    /**
     * 本机直接投递（供本地调用与集群中继桥接器投递）
     */
    public static boolean deliverLocal(String userType, String userId, String message) {
        String key = buildKey(userType, userId);
        WebSocketServer server = webSocketMap.get(key);
        if (server != null && server.session != null && server.session.isOpen()) {
            try {
                server.sendMessage(message);
                return true;
            } catch (IOException e) {
                log.error("本地投递消息失败，用户: {}, 异常: {}", key, e.getMessage());
            }
        }
        return false;
    }

    /**
     * 本机指定用户类型广播（供本地调用与集群中继桥接器投递）
     */
    public static void deliverLocalBroadcast(String userType, String message) {
        webSocketMap.forEach((key, server) -> {
            if (key.startsWith(userType + "_")) {
                try {
                    server.sendMessage(message);
                } catch (IOException e) {
                    log.error("本地广播消息失败，key: {}, error: {}", key, e.getMessage());
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

    /**
     * 测试辅助：清空所有连接与重置计数
     */
    static void resetForTesting() {
        webSocketMap.clear();
        onlineCount.set(0);
    }
}
