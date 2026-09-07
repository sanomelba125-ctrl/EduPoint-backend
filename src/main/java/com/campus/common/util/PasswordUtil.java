package com.campus.common.util;

import cn.dev33.satoken.secure.BCrypt;

/**
 * 密码加盐哈希安全加密与比对工具类 (基于 BCrypt 国际标准算法)
 */
public class PasswordUtil {

    /**
     * 对明文密码进行 BCrypt 动态加盐哈希加密
     *
     * @param rawPassword 明文密码 (例如: "123", "123456")
     * @return 60位的加盐哈希密文字符串 (内部自动内嵌动态 Salt)
     */
    public static String encrypt(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return null;
        }
        return BCrypt.hashpw(rawPassword.trim(), BCrypt.gensalt());
    }

    /**
     * 校验明文密码与数据库加盐密文是否匹配
     *
     * @param rawPassword     用户输入的明文密码
     * @param encodedPassword 数据库中保存的 BCrypt 加盐密文
     * @return true 匹配成功，false 密码错误
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null || encodedPassword.isBlank()) {
            return false;
        }
        try {
            return BCrypt.checkpw(rawPassword.trim(), encodedPassword.trim());
        } catch (Exception e) {
            return false;
        }
    }

    public static void main(String[] args) {
        System.out.println("HASH_123: " + encrypt("123"));
        System.out.println("HASH_123456: " + encrypt("123456"));
    }
}
