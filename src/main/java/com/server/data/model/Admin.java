package com.server.data.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理员模型类
 * 对应JSON结构：{"username":"admin","password":"pwd","passwordHash":"$2a$12$xxx","permissions":["ac.web.*"]}
 * 适配BCrypt密码加密、Web权限校验
 */
public class Admin {
    // 管理员用户名（唯一）
    private String username;
    // BCrypt加密后的密码哈希（不存储明文密码）
    private String passwordHash;
    // 权限列表（如ac.web.announcement、ac.web.*）
    private List<String> permissions = new ArrayList<>();
    // 登录密码（仅用于登录请求接收，不持久化）
    private transient String password;

    // 无参构造（Gson序列化/反序列化必需）
    public Admin() {}

    // 有参构造（便捷创建）
    public Admin(String username, String passwordHash, List<String> permissions) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.permissions = permissions != null ? permissions : new ArrayList<>();
    }

    // ====================== Getter/Setter ======================
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}