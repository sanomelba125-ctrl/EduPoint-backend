package com.campus.common.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.UUID;

/**
 * 全链路 traceId HTTP 边界过滤器 (P3-2 / P5-3)
 * 优先级最高，在请求进入 Controller 前自动植入 MDC，并在响应头回写 X-Trace-Id
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String TRACE_HEADER = "X-Trace-Id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 1. 优先读取上游网关或前端传入的追踪标识，未传则生成全新的 32 位 UUID
        String traceId = httpRequest.getHeader(TRACE_HEADER);
        if (!StringUtils.hasText(traceId)) {
            traceId = httpRequest.getHeader(TRACE_ID_KEY);
        }
        if (!StringUtils.hasText(traceId)) {
            traceId = httpRequest.getParameter(TRACE_ID_KEY);
        }
        if (!StringUtils.hasText(traceId)) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        } else {
            traceId = traceId.trim();
        }

        // 2. 注入当前线程 MDC 上下文
        MDC.put(TRACE_ID_KEY, traceId);

        // 3. 回写 HTTP 响应头供前端和调用方跟踪排查
        httpResponse.setHeader(TRACE_HEADER, traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            // 4. 请求结束彻底清理，杜绝 Servlet 线程池复用污染
            MDC.remove(TRACE_ID_KEY);
        }
    }
}
