package com.campus.common.websocket;

/**
 * WebSocket 端点契约常量
 *
 * <p>单独抽类的原因：Java 不允许类自身的注解引用本类字段（注解值在类外作用域解析），
 * 而 {@code @ServerEndpoint} 的路径与子协议必须由常量引用，避免与前端拼接地址脱节。</p>
 */
public final class WebSocketConstants {

    /** 端点路径前缀 */
    public static final String PATH_PREFIX = "/ws/";

    /** 路径参数名：连接方类型 (admin / student) */
    public static final String PARAM_USER_TYPE = "userType";

    /** 路径参数名：连接方标识 */
    public static final String PARAM_USER_ID = "userId";

    /** 端点路径，必须与前端拼接的连接地址保持一致 */
    public static final String ENDPOINT_PATH =
            PATH_PREFIX + "{" + PARAM_USER_TYPE + "}/{" + PARAM_USER_ID + "}";

    /**
     * 承载 token 的子协议标记。
     * 浏览器 WebSocket 无法自定义请求头，客户端按 [标记, token] 顺序提交子协议，
     * 服务端固定回选该标记，既满足子协议协商又不会把 token 写进握手响应头。
     * 前端 mobile-app 与 admin-web 中的同名常量必须与此处保持一致。
     */
    public static final String PROTOCOL_MARKER = "satoken";

    private WebSocketConstants() {
    }
}
