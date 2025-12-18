package com.server.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * BCrypt 加密工具类（单例封装）
 * 适配 JDK 17，提供密码加密、验证功能
 * 依赖 jbcrypt 0.4 包（pom.xml 已引入）
 */
public class BCryptUtils {
    // 私有化构造，禁止实例化
    private BCryptUtils() {}

    /**
     * 加密密码（生成随机盐值）
     * @param plainPassword 明文密码
     * @return 加密后的密码哈希
     */
    public static String encrypt(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("明文密码不能为空");
        }
        // 生成盐值（强度12，平衡安全性与性能）
        String salt = BCrypt.gensalt(12);
        return BCrypt.hashpw(plainPassword, salt);
    }

    /**
     * 验证密码是否匹配
     * @param plainPassword 明文密码
     * @param hashedPassword 加密后的密码哈希
     * @return true=匹配，false=不匹配
     */
    public static boolean verify(String plainPassword, String hashedPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            return false;
        }
        if (hashedPassword == null || hashedPassword.isEmpty()) {
            return false;
        }
        try {
            return BCrypt.checkpw(plainPassword, hashedPassword);
        } catch (Exception e) {
            // 密码格式错误时返回false
            return false;
        }
    }
}