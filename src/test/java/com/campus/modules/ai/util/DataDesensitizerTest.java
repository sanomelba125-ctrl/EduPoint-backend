package com.campus.modules.ai.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 隐私脱敏过滤器单测（P1：密码「是/为/is」写法必须覆盖，且不得误伤普通提问）
 */
class DataDesensitizerTest {

    @Test
    @DisplayName("手机号按 3-4-4 分段脱敏，保留首尾便于人工核对")
    void testPhoneMasked() {
        String result = DataDesensitizer.desensitize("我的手机号是13812345678，帮我查一下积分");
        assertEquals("我的手机号是138****5678，帮我查一下积分", result);
    }

    @Test
    @DisplayName("18 位身份证脱敏，且不会把中间 11 位数字误判成手机号")
    void testIdCardMasked() {
        String result = DataDesensitizer.desensitize("身份证110101199001011234");
        assertEquals("身份证110101********1234", result);
    }

    @Test
    @DisplayName("「密码是123456」必须脱敏（旧正则漏判的口语写法）")
    void testPasswordWithShi() {
        String result = DataDesensitizer.desensitize("密码是123456");
        assertEquals("密码:******", result);
        assertFalse(result.contains("123456"), "明文密码不得残留");
    }

    @Test
    @DisplayName("「密码为 abc12345」必须脱敏")
    void testPasswordWithWei() {
        String result = DataDesensitizer.desensitize("我密码为abc12345");
        assertEquals("我密码:******", result);
        assertFalse(result.contains("abc12345"));
    }

    @Test
    @DisplayName("「密码：123456」冒号写法继续生效")
    void testPasswordWithColon() {
        String result = DataDesensitizer.desensitize("密码：123456");
        assertEquals("密码:******", result);
    }

    @Test
    @DisplayName("「password is secret123」英文连接词必须脱敏")
    void testPasswordWithIs() {
        String result = DataDesensitizer.desensitize("password is secret123");
        assertEquals("password:******", result);
        assertFalse(result.toLowerCase().contains("secret123"));
    }

    @Test
    @DisplayName("「pwd: p@ssw0rd」缩写写法必须脱敏")
    void testPasswordWithPwd() {
        String result = DataDesensitizer.desensitize("pwd: p@ssw0rd");
        assertEquals("pwd:******", result);
    }

    @Test
    @DisplayName("密码后接中文标点时只脱敏密码值，不吞掉后续正文")
    void testPasswordFollowedByChinesePunctuation() {
        String result = DataDesensitizer.desensitize("密码是123456，帮我看看账号安全");
        assertEquals("密码:******，帮我看看账号安全", result);
    }

    @Test
    @DisplayName("不含密码值的普通提问不得被误伤")
    void testPlainQuestionNotMasked() {
        assertEquals("密码错误怎么办", DataDesensitizer.desensitize("密码错误怎么办"));
        assertEquals("我忘记密码了", DataDesensitizer.desensitize("我忘记密码了"));
        assertEquals("密码怎么重置", DataDesensitizer.desensitize("密码怎么重置"));
    }

    @Test
    @DisplayName("同一句话里的手机号与密码同时脱敏")
    void testPhoneAndPasswordTogether() {
        String result = DataDesensitizer.desensitize("手机13812345678，密码是123456");
        assertEquals("手机138****5678，密码:******", result);
        assertFalse(result.contains("123456"));
    }

    @Test
    @DisplayName("null 与空白输入原样返回，不抛异常")
    void testNullAndBlank() {
        assertNull(DataDesensitizer.desensitize(null));
        assertEquals("", DataDesensitizer.desensitize(""));
        assertEquals("   ", DataDesensitizer.desensitize("   "));
    }
}
