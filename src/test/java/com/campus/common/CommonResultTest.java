package com.campus.common;

import com.campus.common.result.Result;
import com.campus.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 统一接口返回对象单元测试
 */
class CommonResultTest {

    @Test
    @DisplayName("测试成功响应结构体与默认属性封装")
    void testSuccessResult() {
        Result<String> result = Result.success("测试数据");
        assertEquals(ResultCode.SUCCESS.getCode(), result.getCode());
        assertEquals("操作成功", result.getMessage());
        assertEquals("测试数据", result.getData());
        assertNotNull(result.getTimestamp());
        assertTrue(result.getTimestamp() > 0);
    }

    @Test
    @DisplayName("测试自定义成功消息响应封装")
    void testSuccessWithCustomMessage() {
        Result<Integer> result = Result.success("登录成功", 100);
        assertEquals(200, result.getCode());
        assertEquals("登录成功", result.getMessage());
        assertEquals(100, result.getData());
    }

    @Test
    @DisplayName("测试错误响应结构体与状态码封装")
    void testErrorResult() {
        Result<Void> result = Result.error(500, "系统未知异常");
        assertEquals(500, result.getCode());
        assertEquals("系统未知异常", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    @DisplayName("测试ResultCode枚举状态码映射与分段规范")
    void testResultCodeEnum() {
        // HTTP 基础映射
        assertEquals(200, ResultCode.SUCCESS.getCode());
        assertEquals(400, ResultCode.BAD_REQUEST.getCode());
        assertEquals(401, ResultCode.UNAUTHORIZED.getCode());
        assertEquals(403, ResultCode.FORBIDDEN.getCode());
        assertEquals(404, ResultCode.NOT_FOUND.getCode());
        assertEquals(409, ResultCode.CONFLICT.getCode());
        assertEquals(500, ResultCode.ERROR.getCode());

        // 领域分段错误码 (2xxxx: 用户域, 3xxxx: 活动域, 4xxxx: 积分与商城域)
        assertEquals(20001, ResultCode.USER_NOT_EXIST.getCode());
        assertEquals(20002, ResultCode.PASSWORD_ERROR.getCode());
        assertEquals(30001, ResultCode.ACTIVITY_EXPIRED.getCode());
        assertEquals(30002, ResultCode.ALREADY_CHECKED_IN.getCode());
        assertEquals(40001, ResultCode.POINTS_NOT_ENOUGH.getCode());
        assertEquals(40002, ResultCode.GIFT_STOCK_EMPTY.getCode());

        // 工厂方法与防御
        assertEquals(ResultCode.SUCCESS, ResultCode.of(200));
        assertEquals(ResultCode.BUSINESS_ERROR, ResultCode.of(501));
        assertEquals(ResultCode.ACTIVITY_EXPIRED, ResultCode.of(30001));
        assertEquals(ResultCode.ERROR, ResultCode.of(999999));
        assertEquals(ResultCode.ERROR, ResultCode.of(null));
    }

    @Test
    @DisplayName("守卫测试：断言ResultCode全部枚举状态码全局唯一，杜绝撞车")
    void testResultCodeUniqueness() {
        java.util.Set<Integer> seenCodes = new java.util.HashSet<>();
        for (ResultCode rc : ResultCode.values()) {
            boolean isUnique = seenCodes.add(rc.getCode());
            assertTrue(isUnique, "发现重复的状态码定义: code=" + rc.getCode() + ", enum=" + rc.name());
            assertEquals(rc, ResultCode.of(rc.getCode()), "ResultCode.of() 无法反查出对应枚举: " + rc.name());
        }
    }

    @Test
    @DisplayName("测试BusinessException对ResultCode及动态详情的规范包装")
    void testBusinessExceptionWithResultCode() {
        com.campus.common.exception.BusinessException ex1 =
                new com.campus.common.exception.BusinessException(ResultCode.POINTS_NOT_ENOUGH);
        assertEquals(40001, ex1.getCode());
        assertEquals("您的可用积分余额不足", ex1.getMessage());

        com.campus.common.exception.BusinessException ex2 =
                new com.campus.common.exception.BusinessException(ResultCode.POINTS_NOT_ENOUGH, "当前余额: 10 pts，本次需要: 50 pts");
        assertEquals(40001, ex2.getCode());
        assertTrue(ex2.getMessage().contains("当前余额: 10 pts"));
        assertTrue(ex2.getMessage().contains("您的可用积分余额不足"));
    }
}
