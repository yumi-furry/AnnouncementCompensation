package com.server.data.model;

import com.server.util.TimeUtils;

import java.util.UUID;

/**
 * 邮箱验证码模型类
 * 对应JSON结构：{"id":"xxx","email":"player@example.com","code":"123456","type":"REGISTER","expireTime":"2025-12-19 10:05","createTime":"2025-12-19 10:00"}
 */
public class EmailVerificationCode {
    // 验证码类型枚举
    public enum CodeType {
        REGISTER,    // 注册验证
        RESET_PASSWORD,  // 重置密码
        CHANGE_EMAIL     // 修改邮箱
    }

    // 数据库ID
    private int id;
    // 验证码唯一ID
    private String codeId;
    // 邮箱地址
    private String email;
    // 验证码
    private String code;
    // 验证码类型
    private CodeType type;
    // 过期时间
    private String expireTime;
    // 创建时间
    private String createTime;
    // 过期时间（别名）
    private String expiresAt;

    // 无参构造
    public EmailVerificationCode() {}

    // 有参构造
    public EmailVerificationCode(String email, CodeType type) {
        this.codeId = UUID.randomUUID().toString().replace("-", "");
        this.email = email;
        this.code = generateRandomCode(6);
        this.type = type;
        this.createTime = TimeUtils.getCurrentTimeStr();
        this.expireTime = calculateExpireTime(5); // 默认5分钟过期
        this.expiresAt = this.expireTime;
    }

    // 数据库构造方法
    public EmailVerificationCode(int id, String email, String code, CodeType type, String expiresAt) {
        this.id = id;
        this.email = email;
        this.code = code;
        this.type = type;
        this.expiresAt = expiresAt;
        this.expireTime = expiresAt;
        this.createTime = TimeUtils.getCurrentTimeStr();
    }

    // 生成随机验证码
    private String generateRandomCode(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append((int) (Math.random() * 10));
        }
        return sb.toString();
    }

    // 计算过期时间（单位：分钟）
    private String calculateExpireTime(int minutes) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime expire = now.plusMinutes(minutes);
        return expire.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    // 检查验证码是否过期
    public boolean isExpired() {
        return TimeUtils.getCurrentTimeStr().compareTo(expireTime) > 0;
    }

    // 检查验证码是否匹配
    public boolean isCodeValid(String code) {
        return !isExpired() && this.code.equals(code);
    }

    // ====================== Getter/Setter ======================
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    // 向后兼容的方法
    public String getIdString() {
        return codeId;
    }

    public void setId(String id) {
        this.codeId = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public CodeType getType() {
        return type;
    }

    public void setType(CodeType type) {
        this.type = type;
    }

    public String getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(String expireTime) {
        this.expireTime = expireTime;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
        this.expireTime = expiresAt; // 保持同步
    }
}