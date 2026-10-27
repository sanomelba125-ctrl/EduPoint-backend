package com.campus.modules.ai.agent.tool;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P1：工具调用日志的隐私边界回归。
 *
 * <p>入参必须脱敏后再打印，事实数据只允许输出字符数，不得把手机号、密码、
 * 订单号、提货码原样写进日志。</p>
 */
class ToolRegistryLogPrivacyTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(ToolRegistry.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    @DisplayName("工具执行日志不得出现完整手机号、明文密码、订单号与提货码")
    void toolLogsMustNotLeakSensitivePayload() {
        AiTool tool = mock(AiTool.class);
        when(tool.getName()).thenReturn("query_student_contact");
        when(tool.getDescription()).thenReturn("查询学生联系方式");
        when(tool.getToolDefinition()).thenReturn(new JSONObject());
        when(tool.execute(anyLong(), any())).thenReturn(
                "{\"phone\":\"13812345678\",\"orderNo\":\"ORD20261027001\",\"pickupCode\":\"A1B2C3\"}");

        ToolRegistry registry = new ToolRegistry(List.of(tool));
        registry.init();

        registry.executeTool("call-1", "query_student_contact",
                "{\"phone\":\"13812345678\",\"password\":\"密码是secret789\"}", 20220101L);

        String logs = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));

        assertFalse(logs.contains("13812345678"), "日志泄露了完整手机号");
        assertFalse(logs.contains("secret789"), "日志泄露了明文密码");
        assertFalse(logs.contains("ORD20261027001"), "日志泄露了订单号");
        assertFalse(logs.contains("A1B2C3"), "日志泄露了提货码");
        assertTrue(logs.contains("事实数据字符数"), "应保留脱敏后的可观测摘要");
    }
}
