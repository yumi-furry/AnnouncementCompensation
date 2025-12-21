package com.server.data.model;

import com.server.util.BCryptUtils;
import com.server.util.TimeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户账号模型类
 * 对应JSON结构：{"username":"player1","passwordHash":"$2a$...","email":"player@example.com","gameUUID":"玩家UUID","verificationKey":"验证密钥","isVerified":true,"createTime":"2025-12-19 09:00","lastLoginTime":"2025-12-19 10:00"}
 */
public class User {
    // 数据库ID
    private int id;
    // 用户名（唯一）
    private String username;
    // 密码哈希值
    private String passwordHash;
    // 邮箱地址
    private String email;
    // 游戏角色UUID
    private String gameUUID;
    // 邮箱验证密钥
    private String verificationKey;
    // 是否已验证邮箱
    private boolean isVerified = false;
    // 是否已绑定游戏角色
    private boolean isGameRoleBound = false;
    // 创建时间
    private String createTime;
    // 更新时间
    private String updatedAt;
    // 最后登录时间
    private String lastLoginTime;
    // QQ绑定信息
    private QQBinding qqBinding;
    // 用户权限列表
    private List<String> permissions = new ArrayList<>();

    // 无参构造
    public User() {}

    // 注册时的构造方法
    public User(String username, String password, String email) {
        this.username = username;
        this.passwordHash = BCryptUtils.encrypt(password);
        this.email = email;
        this.createTime = TimeUtils.getCurrentTimeStr();
        this.verificationKey = generateVerificationKey();
    }

    // 生成邮箱验证密钥
    private String generateVerificationKey() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    // 验证密码
    public boolean verifyPassword(String password) {
        return BCryptUtils.verify(password, this.passwordHash);
    }

    // 更新密码
    public void updatePassword(String newPassword) {
        this.passwordHash = BCryptUtils.encrypt(newPassword);
    }

    // 更新最后登录时间
    public void updateLastLoginTime() {
        this.lastLoginTime = TimeUtils.getCurrentTimeStr();
    }

    // ====================== Getter/Setter ======================
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getGameUUID() {
        return gameUUID;
    }

    public void setGameUUID(String gameUUID) {
        this.gameUUID = gameUUID;
    }

    public String getVerificationKey() {
        return verificationKey;
    }

    public void setVerificationKey(String verificationKey) {
        this.verificationKey = verificationKey;
    }

    public boolean isVerified() {
        return isVerified;
    }

    public void setVerified(boolean verified) {
        isVerified = verified;
    }

    public boolean isGameRoleBound() {
        return isGameRoleBound;
    }

    public void setGameRoleBound(boolean gameRoleBound) {
        isGameRoleBound = gameRoleBound;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(String lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }

    public QQBinding getQqBinding() {
        return qqBinding;
    }

    public void setQqBinding(QQBinding qqBinding) {
        this.qqBinding = qqBinding;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    public String getCreatedAt() {
        return createTime;
    }

    public void setCreatedAt(String createdAt) {
        this.createTime = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    // QQ相关便捷方法
    public String getQqNumber() {
        return qqBinding != null ? qqBinding.getQqOpenId() : null;
    }

    public void setQqNumber(String qqNumber) {
        if (qqBinding == null) {
            qqBinding = new QQBinding();
        }
        qqBinding.setQqOpenId(qqNumber);
    }

    public String getMinecraftUuid() {
        return gameUUID;
    }

    public void setMinecraftUuid(String minecraftUuid) {
        this.gameUUID = minecraftUuid;
    }

    // 检查是否有指定权限
    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }

    // 添加权限
    public void addPermission(String permission) {
        if (permissions == null) {
            permissions = new ArrayList<>();
        }
        if (!permissions.contains(permission)) {
            permissions.add(permission);
        }
    }

    // 移除权限
    public void removePermission(String permission) {
        if (permissions != null) {
            permissions.remove(permission);
        }
    }
}