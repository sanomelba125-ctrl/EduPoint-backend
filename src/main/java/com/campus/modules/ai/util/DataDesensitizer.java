package com.campus.modules.ai.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 隐私敏感数据脱敏过滤器
 * 时序图组件: DataDesensitizer
 * 对应设计: 过滤手机号、身份证号、明文密码、私密凭证等，防止隐私数据外泄给大模型
 */
public final class DataDesensitizer {

    private DataDesensitizer() {
    }

    /** 手机号正则 (11位数字) */
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(1[3-9]\\d)(\\d{4})(\\d{4})(?!\\d)");

    /** 身份证号正则 (18位) */
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(?<!\\d)(\\d{6})(\\d{8})(\\d{3}[0-9Xx])(?!\\d)");

    /** 密码敏感词匹配 (如: 密码是123456 / pwd: xxx) */
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("(?i)(密码|pwd|password)[\\s:=:：]+([\\S]{4,20})");

    /**
     * 对提问文本进行安全脱敏过滤
     *
     * @param text 原始输入
     * @return 脱敏净化后的文本
     */
    public static String desensitize(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String result = text;

        // 1. 手机号脱敏: 13812345678 -> 138****5678
        Matcher phoneMatcher = PHONE_PATTERN.matcher(result);
        result = phoneMatcher.replaceAll("$1****$3");

        // 2. 身份证脱敏: 110101199001011234 -> 110101********1234
        Matcher idMatcher = ID_CARD_PATTERN.matcher(result);
        result = idMatcher.replaceAll("$1********$3");

        // 3. 密码脱敏: 密码是123456 -> 密码是******
        Matcher pwdMatcher = PASSWORD_PATTERN.matcher(result);
        result = pwdMatcher.replaceAll("$1:******");

        return result;
    }
}
