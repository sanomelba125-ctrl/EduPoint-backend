package com.campus.common;

import com.campus.common.util.PasswordUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 密码加密与比对工具单测 (PasswordUtilTest)
 */
class PasswordUtilTest {

    @Test
    @DisplayName("测试相同明文密码每次加密生成的哈希密文不同（基于 BCrypt 随机盐值机制）")
    void testEncode_RandomSalt_ShouldProduceDifferentHashes() {
        String rawPassword = "admin123456";

        String hash1 = PasswordUtil.encode(rawPassword);
        String hash2 = PasswordUtil.encode(rawPassword);

        assertNotNull(hash1, "加密生成的密文不应为空");
        assertNotNull(hash2, "加密生成的密文不应为空");
        assertNotEquals(hash1, hash2, "由于使用了随机盐，相同明文多次加密的密文必须不同");
    }

    @Test
    @DisplayName("测试 matches(\"123456\", hash) 能够成功通过比对")
    void testMatches_CorrectPassword_ShouldReturnTrue() {
        String rawPassword = "123456";
        String encodedHash = PasswordUtil.encode(rawPassword);

        boolean isMatch = PasswordUtil.matches(rawPassword, encodedHash);

        assertTrue(isMatch, "正确密码与对应哈希值进行比对必须返回 true");
    }

    @Test
    @DisplayName("测试错误密码进行比对时返回 false")
    void testMatches_WrongPassword_ShouldReturnFalse() {
        String rawPassword = "123456";
        String wrongPassword = "wrongPassword666";
        String encodedHash = PasswordUtil.encode(rawPassword);

        boolean isMatch = PasswordUtil.matches(wrongPassword, encodedHash);

        assertFalse(isMatch, "错误密码与哈希值进行比对必须返回 false");
    }

    @Test
    @DisplayName("测试空字符串或篡改后的哈希值无法通过校验")
    void testMatches_TamperedOrEmpty_ShouldReturnFalse() {
        String rawPassword = "password123";
        String encodedHash = PasswordUtil.encode(rawPassword);

        // 空串
        assertFalse(PasswordUtil.matches("", encodedHash), "空密码不能通过校验");

        // 篡改密文
        String tamperedHash = encodedHash.substring(0, encodedHash.length() - 4) + "abcd";
        assertFalse(PasswordUtil.matches(rawPassword, tamperedHash), "篡改后的密文不能通过校验");
    }
}
