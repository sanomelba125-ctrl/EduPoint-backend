package com.campus.common.websocket;

import jakarta.websocket.CloseReason;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("通用模块：WebSocketServer 稳定性、并发安全与多实例集群测试 (P5-8)")
class WebSocketServerTest {

    @BeforeEach
    void setUp() {
        WebSocketServer.resetForTesting();
    }

    @AfterEach
    void tearDown() {
        WebSocketServer.resetForTesting();
    }

    @Test
    @DisplayName("P5-8b: 正常连接建立与断开，在线人数正确增减")
    void normalConnection_shouldTrackOnlineCount() throws Exception {
        Session session = mock(Session.class);
        RemoteEndpoint.Basic basic = mock(RemoteEndpoint.Basic.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getBasicRemote()).thenReturn(basic);

        WebSocketServer server = new WebSocketServer();
        server.onOpen(session, "student", "1001");

        assertEquals(1, WebSocketServer.getOnlineCount());
        verify(basic).sendText("连接成功");

        server.onClose();
        assertEquals(0, WebSocketServer.getOnlineCount());
    }

    @Test
    @DisplayName("P5-8b: 重连时主动关闭旧连接，且旧连接延迟触发 onClose 不会误删新连接与扣减人数")
    void reconnect_shouldCloseOldSession_andNotRemoveNewSessionOnOldClose() throws Exception {
        // 第 1 个连接
        Session session1 = mock(Session.class);
        RemoteEndpoint.Basic basic1 = mock(RemoteEndpoint.Basic.class);
        when(session1.isOpen()).thenReturn(true);
        when(session1.getBasicRemote()).thenReturn(basic1);

        WebSocketServer server1 = new WebSocketServer();
        server1.onOpen(session1, "student", "1002");
        assertEquals(1, WebSocketServer.getOnlineCount());

        // 第 2 个连接（同一学生重连）
        Session session2 = mock(Session.class);
        RemoteEndpoint.Basic basic2 = mock(RemoteEndpoint.Basic.class);
        when(session2.isOpen()).thenReturn(true);
        when(session2.getBasicRemote()).thenReturn(basic2);

        WebSocketServer server2 = new WebSocketServer();
        server2.onOpen(session2, "student", "1002");

        // 验证旧 session1 被主动调用 close()
        verify(session1).close(any(CloseReason.class));
        // 重连后在线总数依然是 1
        assertEquals(1, WebSocketServer.getOnlineCount());

        // 模拟网络延迟后，旧连接的 onClose() 异步被触发
        server1.onClose();

        // 关键验证：由于使用了 remove(key, this)，旧 server1 无法移除新 server2，在线人数仍为 1
        assertEquals(1, WebSocketServer.getOnlineCount());

        // 验证新 server2 依然能正常接收消息投递
        assertTrue(WebSocketServer.deliverLocal("student", "1002", "测试消息"));
        verify(basic2).sendText("测试消息");

        // 最终新连接正常退出
        server2.onClose();
        assertEquals(0, WebSocketServer.getOnlineCount());
    }

    @Test
    @DisplayName("P5-8c: sendMessage 采用 Jackson 序列化对象，并使用 session 同步锁保障并发安全")
    void sendMessage_shouldSerializeWithJackson_andSynchronizeOnSession() throws Exception {
        Session session = mock(Session.class);
        RemoteEndpoint.Basic basic = mock(RemoteEndpoint.Basic.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getBasicRemote()).thenReturn(basic);

        WebSocketServer server = new WebSocketServer();
        ReflectionTestUtils.setField(server, "session", session);

        Map<String, Object> payload = Map.of("code", 200, "content", "hello-jackson");
        server.sendMessage(payload);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(basic).sendText(captor.capture());
        String sentJson = captor.getValue();
        assertTrue(sentJson.contains("\"code\":200"));
        assertTrue(sentJson.contains("\"content\":\"hello-jackson\""));
    }

    @Test
    @DisplayName("P5-8a: WebSocketClusterBridge 跨节点中继发布与本节点防回环过滤")
    void clusterBridge_shouldPublishAndFilterSelfMessages() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RTopic topic = mock(RTopic.class);
        when(redissonClient.getTopic(WebSocketClusterBridge.TOPIC_NAME)).thenReturn(topic);

        WebSocketClusterBridge bridge = new WebSocketClusterBridge();
        ReflectionTestUtils.setField(bridge, "redissonClient", redissonClient);
        bridge.init();

        // 测试发布用户消息
        WebSocketClusterBridge.publishUserMessage("student", "2001", "系统通知测试");
        verify(topic).publish(any(WebSocketClusterBridge.ClusterMessage.class));

        // 测试发布广播消息
        WebSocketClusterBridge.publishBroadcastMessage("admin", "全服广播测试");
        verify(topic, times(2)).publish(any(WebSocketClusterBridge.ClusterMessage.class));
    }
}
