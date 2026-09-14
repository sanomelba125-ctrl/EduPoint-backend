package com.campus.common.websocket;

import cn.dev33.satoken.stp.StpUtil;
import com.campus.common.auth.AuthUtil;
import com.campus.config.WebSocketAuthProperties;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * WebSocket 握手鉴权配置器
 *
 * <p>WebSocket 握手由 Tomcat 的 WsFilter 处理，不会进入 DispatcherServlet，
 * 因此 {@code SaInterceptor} 这类 Spring MVC 拦截器对 WebSocket 完全失效，鉴权必须在这里做。</p>
 *
 * <p>依次校验三件事，任意一项不通过立即拒绝握手：</p>
 * <ol>
 *   <li><b>来源</b>：请求头 Origin 命中白名单；非浏览器客户端不携带 Origin 时按配置决定放行与否；</li>
 *   <li><b>凭据</b>：token 有效，能解析出登录身份；</li>
 *   <li><b>身份</b>：路径上的 userType / userId 与 token 身份完全一致（管理员只能连管理端，学生只能连自己）。</li>
 * </ol>
 *
 * <p>本类由容器反射实例化，无法使用依赖注入，配置通过 {@link WebSocketAuthProperties#current()} 读取。
 * 任何日志都不得输出完整 token。</p>
 */
@Slf4j
public class WebSocketAuthConfigurator extends ServerEndpointConfig.Configurator {

    /** 管理端路径标识 */
    private static final String USER_TYPE_ADMIN = "admin";

    /** 学生端路径标识 */
    private static final String USER_TYPE_STUDENT = "student";

    /** 承载 token 的请求头（浏览器 WebSocket 无法自定义请求头，故用子协议承载） */
    private static final String HEADER_SUB_PROTOCOL = HandshakeRequest.SEC_WEBSOCKET_PROTOCOL;

    /** 请求来源头 */
    private static final String HEADER_ORIGIN = "Origin";

    /** query 回退参数名：?token=xxx */
    private static final String QUERY_TOKEN = "token";

    @Override
    public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
        WebSocketAuthProperties properties = WebSocketAuthProperties.current();

        // 1. 来源校验
        String origin = firstHeaderValue(request, HEADER_ORIGIN);
        if (!isOriginAllowed(properties, origin)) {
            reject("Origin 校验未通过(origin=" + origin + ")");
        }

        // 2. 凭据校验
        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            reject("握手请求未携带 token");
        }
        String loginId = resolveLoginId(token);
        if (loginId == null) {
            reject("token 无效或已失效(token=" + maskToken(token) + ")");
        }

        // 3. 身份一致性校验：路径身份必须与 token 身份完全一致。
        // ServerEndpointConfig 不提供路径参数，只能从请求 URI 解析。
        URI requestUri = request.getRequestURI();
        String userType = parsePathParameter(requestUri, WebSocketConstants.PARAM_USER_TYPE);
        String userId = parsePathParameter(requestUri, WebSocketConstants.PARAM_USER_ID);
        String expectedPrefix = expectedLoginPrefix(userType);
        if (expectedPrefix == null) {
            reject("非法的连接方类型(userType=" + userType + ")");
        }
        if (!StringUtils.hasText(userId)
                || !loginId.startsWith(expectedPrefix)
                || !loginId.substring(expectedPrefix.length()).equals(userId.trim())) {
            reject("路径身份与登录身份不一致(userType=" + userType + ", userId=" + userId
                    + ", loginId=" + loginId + ")");
        }

        log.debug("WebSocket 握手鉴权通过: userType={}, userId={}", userType, userId);
    }

    /**
     * 固定回选子协议标记。
     * 客户端提交的子协议为 [标记, token]，这里只回选标记本身，
     * 既保证严格校验子协议的客户端能建连，又不会把 token 写回响应头。
     */
    @Override
    public String getNegotiatedSubprotocol(List<String> supported, List<String> requested) {
        if (supported != null && supported.contains(WebSocketConstants.PROTOCOL_MARKER)
                && requested != null && requested.contains(WebSocketConstants.PROTOCOL_MARKER)) {
            return WebSocketConstants.PROTOCOL_MARKER;
        }
        return super.getNegotiatedSubprotocol(supported, requested);
    }

    /**
     * 提取 token：优先取子协议（浏览器 WebSocket 无法自定义请求头），取不到时回退 query 参数 ?token=
     */
    private static String extractToken(HandshakeRequest request) {
        for (String raw : headerValues(request, HEADER_SUB_PROTOCOL)) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            for (String item : raw.split(",")) {
                String candidate = item.trim();
                if (StringUtils.hasText(candidate) && !WebSocketConstants.PROTOCOL_MARKER.equals(candidate)) {
                    return candidate;
                }
            }
        }
        Map<String, List<String>> parameters = request.getParameterMap();
        if (parameters != null) {
            List<String> values = parameters.get(QUERY_TOKEN);
            if (values != null && !values.isEmpty() && StringUtils.hasText(values.get(0))) {
                return values.get(0).trim();
            }
        }
        return null;
    }

    /**
     * Origin 白名单校验：白名单为空则拒绝；命中 {@code *} 则全放行；
     * 请求未携带 Origin（微信小程序原生连接、部分真机调试）时按 allowMissingOrigin 配置决定。
     */
    private static boolean isOriginAllowed(WebSocketAuthProperties properties, String origin) {
        if (properties == null) {
            log.error("WebSocket 鉴权配置尚未初始化，已拒绝握手");
            return false;
        }
        List<String> allowedOrigins = properties.getAllowedOrigins();
        if (allowedOrigins != null) {
            for (String allowed : allowedOrigins) {
                if (allowed != null && "*".equals(allowed.trim())) {
                    return true;
                }
            }
        }
        if (!StringUtils.hasText(origin)) {
            return properties.isAllowMissingOrigin();
        }
        if (allowedOrigins == null) {
            return false;
        }
        String normalizedOrigin = normalizeOrigin(origin);
        for (String allowed : allowedOrigins) {
            if (StringUtils.hasText(allowed) && normalizeOrigin(allowed).equals(normalizedOrigin)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeOrigin(String origin) {
        String normalized = origin.trim().toLowerCase(Locale.ROOT);
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * 按 /ws/{userType}/{userId} 的位置从请求 URI 上解析路径参数。
     * {@link ServerEndpointConfig} 只提供端点模板，请求侧的路径参数无法通过它获取，
     * 且 {@link HandshakeRequest#getParameterMap()} 只包含 query 参数，因此这里以请求 URI 为准。
     */
    private static String parsePathParameter(URI requestUri, String name) {
        if (requestUri == null) {
            return null;
        }
        String path = requestUri.getPath() != null ? requestUri.getPath() : requestUri.toString();
        int prefixIndex = path.indexOf(WebSocketConstants.PATH_PREFIX);
        if (prefixIndex < 0) {
            return null;
        }
        String[] segments = path.substring(prefixIndex + WebSocketConstants.PATH_PREFIX.length()).split("/");
        if (WebSocketConstants.PARAM_USER_TYPE.equals(name)
                && segments.length >= 1 && StringUtils.hasText(segments[0])) {
            return segments[0].trim();
        }
        if (WebSocketConstants.PARAM_USER_ID.equals(name)
                && segments.length >= 2 && StringUtils.hasText(segments[1])) {
            return segments[1].trim();
        }
        return null;
    }

    /** 路径类型对应的登录身份前缀 */
    private static String expectedLoginPrefix(String userType) {
        if (USER_TYPE_ADMIN.equalsIgnoreCase(userType)) {
            return AuthUtil.ADMIN_PREFIX;
        }
        if (USER_TYPE_STUDENT.equalsIgnoreCase(userType)) {
            return AuthUtil.STUDENT_PREFIX;
        }
        return null;
    }

    /** 依据 token 解析登录身份，无效或已失效返回 null */
    private static String resolveLoginId(String token) {
        try {
            Object loginId = StpUtil.getLoginIdByToken(token);
            return loginId != null ? loginId.toString() : null;
        } catch (Exception e) {
            // 不输出异常详情，避免潜在凭据泄漏
            log.debug("WebSocket 握手 token 解析失败: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    /** token 脱敏，仅保留前 4 位用于排查，日志中不得出现完整凭据 */
    private static String maskToken(String token) {
        if (!StringUtils.hasText(token)) {
            return "<empty>";
        }
        return token.length() <= 4 ? "****" : token.substring(0, 4) + "****";
    }

    private static String firstHeaderValue(HandshakeRequest request, String name) {
        List<String> values = headerValues(request, name);
        return values.isEmpty() ? null : values.get(0);
    }

    /** 请求头名大小写不敏感：先按原名取，取不到再按全小写取 */
    private static List<String> headerValues(HandshakeRequest request, String name) {
        Map<String, List<String>> headers = request.getHeaders();
        if (headers == null || headers.isEmpty()) {
            return List.of();
        }
        List<String> values = headers.get(name);
        if (values == null) {
            values = headers.get(name.toLowerCase(Locale.ROOT));
        }
        return values != null ? values : List.of();
    }

    /**
     * 拒绝握手。
     * JSR-356 的 {@link HandshakeResponse} 不支持设置状态码，只能通过抛出异常中断升级，
     * 客户端表现为握手失败（不会建立连接、拿不到 101 Switching Protocols）。
     */
    private static void reject(String reason) {
        log.warn("WebSocket 握手鉴权失败，已拒绝连接: {}", reason);
        throw new SecurityException("WebSocket 握手鉴权失败");
    }
}
