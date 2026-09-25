package com.campus.common;

import com.campus.common.exception.BusinessException;
import com.campus.common.exception.GlobalExceptionHandler;
import com.campus.common.filter.TraceIdFilter;
import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import com.campus.common.util.TraceUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("P3-2 & P5-3: 全链路 traceId 与全局异常脱敏测试")
class TraceAndExceptionTest {

    private TraceIdFilter traceIdFilter;
    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        traceIdFilter = new TraceIdFilter();
        exceptionHandler = new GlobalExceptionHandler();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("TraceIdFilter: 自动生成 traceId 并注入 MDC 与响应头")
    void testTraceIdFilterGeneratesId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> mdcInChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            mdcInChain.set(MDC.get(TraceIdFilter.TRACE_ID_KEY));
        };

        traceIdFilter.doFilter(request, response, chain);

        assertNotNull(mdcInChain.get(), "过滤器链执行期间 MDC 必须包含 traceId");
        assertEquals(32, mdcInChain.get().length(), "生成的 UUID traceId 长度应为 32 位");
        assertEquals(mdcInChain.get(), response.getHeader(TraceIdFilter.TRACE_HEADER), "HTTP 响应头必须回写生成的 X-Trace-Id");
        assertNull(MDC.get(TraceIdFilter.TRACE_ID_KEY), "请求结束后必须从 MDC 清理 traceId 防止复用污染");
    }

    @Test
    @DisplayName("TraceIdFilter: 优先继承前端或网关传入的 X-Trace-Id")
    void testTraceIdFilterInheritsHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String customTraceId = "TRACE-GATEWAY-2026-001";
        request.addHeader(TraceIdFilter.TRACE_HEADER, customTraceId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> mdcInChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            mdcInChain.set(MDC.get(TraceIdFilter.TRACE_ID_KEY));
        };

        traceIdFilter.doFilter(request, response, chain);

        assertEquals(customTraceId, mdcInChain.get(), "MDC 必须继承上游传入的 X-Trace-Id");
        assertEquals(customTraceId, response.getHeader(TraceIdFilter.TRACE_HEADER));
        assertNull(MDC.get(TraceIdFilter.TRACE_ID_KEY));
    }

    @Test
    @DisplayName("TraceUtil: 异步子线程正确透传主线程的 MDC 上下文")
    void testTraceUtilAsyncPropagation() throws InterruptedException {
        String mainTraceId = "MAIN-THREAD-TRACE-888";
        MDC.put(TraceUtil.TRACE_ID_KEY, mainTraceId);

        AtomicReference<String> asyncTraceId = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Runnable task = TraceUtil.wrap(() -> {
            asyncTraceId.set(MDC.get(TraceUtil.TRACE_ID_KEY));
            latch.countDown();
        });

        Thread thread = new Thread(task);
        thread.start();
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        assertEquals(mainTraceId, asyncTraceId.get(), "子线程通过 TraceUtil 包装后必须正确读取主线程的 traceId");
    }

    @Test
    @DisplayName("P5-3: 全局未捕获未知异常绝不泄露堆栈与内部 SQL，统一脱敏")
    void testGlobalExceptionHandlerDesensitization() {
        MDC.put(TraceUtil.TRACE_ID_KEY, "TR-FAIL-001");

        // 构造可能泄露 SQL 结构的异常
        Exception sensitiveEx = new RuntimeException("Syntax error in SQL statement: SELECT * FROM secret_user WHERE pw='123'");
        Result<?> result = exceptionHandler.handleException(sensitiveEx);

        assertEquals(ResultCode.ERROR.getCode(), result.getCode());
        assertFalse(result.getMessage().contains("secret_user"), "响应信息严禁泄露 SQL 表名或内部错误细节");
        assertFalse(result.getMessage().contains("Syntax error"));
        assertTrue(result.getMessage().contains("系统繁忙，请稍后重试"), "必须返回统一友好脱敏提示");
        assertTrue(result.getMessage().contains("TR-FAIL-001"), "必须在提示中包含 traceId 便于运维排查");
        assertEquals("TR-FAIL-001", result.getTraceId(), "Result 实体必须携带 traceId");
    }

    @Test
    @DisplayName("P5-3: 数据库唯一键冲突异常友好脱敏")
    void testDuplicateKeyExceptionDesensitization() {
        MDC.put(TraceUtil.TRACE_ID_KEY, "TR-DUP-002");

        DuplicateKeyException dupEx = new DuplicateKeyException("Duplicate entry 'ACT-10-101' for key 'uk_student_activity'");
        Result<?> result = exceptionHandler.handleDuplicateKeyException(dupEx);

        assertEquals(ResultCode.BAD_REQUEST.getCode(), result.getCode());
        assertFalse(result.getMessage().contains("uk_student_activity"), "严禁将底层唯一索引名泄露给前端");
        assertEquals("数据已存在或已被占用，请勿重复提交", result.getMessage());
        assertEquals("TR-DUP-002", result.getTraceId());
    }

    @Test
    @DisplayName("P5-3: 数据完整性与持久层操作异常友好脱敏")
    void testDataAccessExceptionDesensitization() {
        MDC.put(TraceUtil.TRACE_ID_KEY, "TR-DB-003");

        DataIntegrityViolationException dbEx = new DataIntegrityViolationException("Cannot delete or update a parent row: a foreign key constraint fails");
        Result<?> result = exceptionHandler.handleDataAccessException(dbEx);

        assertEquals(ResultCode.ERROR.getCode(), result.getCode());
        assertFalse(result.getMessage().contains("foreign key constraint"), "严禁向前端返回外键或底层约束异常细节");
        assertTrue(result.getMessage().contains("数据处理异常，请核对参数后重试"));
        assertEquals("TR-DB-003", result.getTraceId());
    }

    @Test
    @DisplayName("业务异常正常返回业务状态码与提示信息，并附带 traceId")
    void testBusinessException() {
        MDC.put(TraceUtil.TRACE_ID_KEY, "TR-BIZ-004");

        BusinessException bizEx = new BusinessException(400, "可用积分余额不足");
        Result<?> result = exceptionHandler.handleBusinessException(bizEx);

        assertEquals(400, result.getCode());
        assertEquals("可用积分余额不足", result.getMessage());
        assertEquals("TR-BIZ-004", result.getTraceId());
    }
}
