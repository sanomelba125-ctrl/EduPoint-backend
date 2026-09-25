package com.campus.common.util;

import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * MDC 全链路跨线程 TraceId 传递工具类 (P3-2)
 */
public final class TraceUtil {

    public static final String TRACE_ID_KEY = "traceId";

    private TraceUtil() {
    }

    /**
     * 获取当前线程绑定的 traceId
     */
    public static String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    /**
     * 获取或生成一个 traceId
     */
    public static String getOrCreateTraceId() {
        String traceId = getTraceId();
        if (!StringUtils.hasText(traceId)) {
            traceId = UUID.randomUUID().toString().replace("-", "");
            MDC.put(TRACE_ID_KEY, traceId);
        }
        return traceId;
    }

    /**
     * 包装 Runnable，支持异步线程透传主线程的 MDC 上下文
     */
    public static Runnable wrap(Runnable runnable) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            if (contextMap != null) {
                MDC.setContextMap(contextMap);
            } else {
                MDC.clear();
            }
            try {
                runnable.run();
            } finally {
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        };
    }

    /**
     * 包装 Callable，支持异步线程透传主线程的 MDC 上下文
     */
    public static <V> Callable<V> wrap(Callable<V> callable) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            if (contextMap != null) {
                MDC.setContextMap(contextMap);
            } else {
                MDC.clear();
            }
            try {
                return callable.call();
            } finally {
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        };
    }
}
