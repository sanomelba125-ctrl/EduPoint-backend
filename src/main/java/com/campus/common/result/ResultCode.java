package com.campus.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

public enum ResultCode {

    // 200 接口正常
    SUCCESS(200, "操作成功"),

    // 4XX 客户端鉴权与路径
    BAD_REQUEST(400, "请求参数有误"),
    UNAUTHORIZED(401, "账号未登录或登录已过�?),
    FORBIDDEN(403, "没有该操作的访问权限"),
    NOT_FOUND(404, "请求�?RESTful 资源不存�?),

    // 5XX 接口与业务错�?    ERROR(500, "系统内部异常，请稍后重试"),
    BUSINESS_ERROR(500, "业务逻辑处理失败"),
    USER_NOT_EXIST(501, "用户不存�?),
    PASSWORD_ERROR(502, "登录密码错误"),
    ACTIVITY_EXPIRED(503, "活动未开始或已截�?),
    ALREADY_CHECKED_IN(504, "您已参与过该活动打卡，请勿重复提�?),
    POINTS_NOT_ENOUGH(505, "您的可用积分余额不足"),
    GIFT_STOCK_EMPTY(506, "该礼物库存已不足");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
