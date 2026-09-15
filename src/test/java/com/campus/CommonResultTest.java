package com.campus;

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
    @DisplayName("测试ResultCode枚举状态码映射")
    void testResultCodeEnum() {
        assertEquals(200, ResultCode.SUCCESS.getCode());
        assertEquals(500, ResultCode.ERROR.getCode());
        assertEquals(401, ResultCode.UNAUTHORIZED.getCode());
        assertEquals(403, ResultCode.FORBIDDEN.getCode());
    }
}
