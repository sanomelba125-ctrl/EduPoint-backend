package com.campus.common.result;

import lombok.Getter;

import java.util.Arrays;

/**
 * 统一接口响应状态码枚举
 * 遵循企业级 RESTful + 领域分段错误码规范（RFC 9110 + 阿里巴巴开发手册分段标准）：
 *
 * 1. 基础协议与 HTTP 标准对齐（100 ~ 999）：
 *    - 200: SUCCESS 操作成功
 *    - 400: BAD_REQUEST 参数有误 / 客户端通用错误
 *    - 401: UNAUTHORIZED 认证缺失或 Token 过期
 *    - 403: FORBIDDEN 无权限操作
 *    - 404: NOT_FOUND 资源不存在
 *    - 405: METHOD_NOT_ALLOWED 请求方法不支持
 *    - 409: CONFLICT 业务状态冲突 / 乐观锁冲突
 *    - 429: TOO_MANY_REQUESTS 请求被限流
 *    - 500: ERROR 系统内部未捕获异常 / 服务端错误
 *    - 501: BUSINESS_ERROR 通用业务逻辑处理失败
 *
 * <p><b>维护约定：新增枚举项时必须确认 code 全局唯一。</b>
 *
 * 2. 领域业务细分错误码（五位定长分段，彻底杜绝与 HTTP 状态码撞车）：
 *    - 2xxxx: 用户身份与权限域 (User & Auth)
 *    - 3xxxx: 活动与打卡核验域 (Activity & Checkin)
 *    - 4xxxx: 积分账户与礼品商城域 (Points & Mall)
 *    - 5xxxx: 荣誉证书与协同通知域 (Certificate & Notice)
 */
@Getter
public enum ResultCode {

    // ==========================================
    // 1. 基础 HTTP 契约对齐
    // ==========================================
    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数有误"),
    UNAUTHORIZED(401, "账号未登录或登录已过期"),
    FORBIDDEN(403, "没有该操作的访问权限"),
    NOT_FOUND(404, "请求的RESTful资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    CONFLICT(409, "数据状态冲突或版本并发冲突"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，触发系统限流保护"),
    ERROR(500, "系统内部异常，请稍后重试"),
    BUSINESS_ERROR(501, "业务逻辑处理失败"),

    // ==========================================
    // 2. 用户与认证域 (2xxxx)
    // ==========================================
    USER_NOT_EXIST(20001, "用户档案不存在"),
    PASSWORD_ERROR(20002, "用户名或密码错误"),
    ACCOUNT_DISABLED(20003, "账号已被冻结或禁用，请联系管理员"),
    USER_ALREADY_EXISTS(20004, "目标学号或工号已存在"),

    // ==========================================
    // 3. 活动与签到核验域 (3xxxx)
    // ==========================================
    ACTIVITY_EXPIRED(30001, "活动未开始或已结束"),
    ALREADY_CHECKED_IN(30002, "您已参与过该活动打卡，请勿重复提交"),
    ACTIVITY_NOT_ENROLLED(30003, "学生尚未报名该活动，无法签到"),
    ACTIVITY_GEOFENCE_FAILED(30004, "当前打卡坐标超出活动地理围栏有效范围"),
    ACTIVITY_SIGNUP_FULL(30005, "活动报名名额已满"),
    ACTIVITY_TIME_CONFLICT(30006, "报名时间与已参与活动冲突"),

    // ==========================================
    // 4. 积分与礼品商城域 (4xxxx)
    // ==========================================
    POINTS_NOT_ENOUGH(40001, "您的可用积分余额不足"),
    GIFT_STOCK_EMPTY(40002, "该礼物库存已不足"),
    ORDER_ALREADY_DELIVERED(40003, "该订单已完成核销提货"),
    ORDER_ALREADY_CANCELLED(40004, "该订单已被取消"),
    ORDER_NOT_FOUND(40005, "目标订单记录不存在"),
    EXCHANGE_TOKEN_INVALID(40006, "兑换防重令牌无效或已过期");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public static ResultCode of(Integer code) {
        if (code == null) return ERROR;
        return Arrays.stream(values())
                .filter(item -> item.getCode().equals(code))
                .findFirst()
                .orElse(ERROR);
    }
}
