package com.server.data.model;

import com.server.util.TimeUtils;

/**
 * 白名单模型类
 * 对应JSON结构：{"uuid":"玩家UUID","playerName":"玩家名","addTime":"2025-12-19 09:00"}
 */
public class WhitelistEntry {
    // 数据库ID
    private int id;
    // 玩家UUID（唯一）
    private String playerUuid;
    // 玩家名称
    private String playerName;
    // 添加时间
    private String addTime;
    // 添加者
    private String addedBy;
    // 添加原因
    private String reason;
    // 创建时间
    private String createdAt;

    // 无参构造
    public WhitelistEntry() {}

    // 有参构造
    public WhitelistEntry(String playerUuid, String playerName) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.addTime = TimeUtils.getCurrentTimeStr();
        this.createdAt = TimeUtils.getCurrentTimeStr();
    }

    // 数据库构造方法
    public WhitelistEntry(int id, String playerUuid, String playerName, String addedBy, String reason) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.addedBy = addedBy;
        this.reason = reason;
        this.addTime = TimeUtils.getCurrentTimeStr();
        this.createdAt = TimeUtils.getCurrentTimeStr();
    }

    // ====================== Getter/Setter ======================
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(String playerUuid) {
        this.playerUuid = playerUuid;
    }

    // 向后兼容的别名方法
    public String getUuid() {
        return getPlayerUuid();
    }

    public void setUuid(String uuid) {
        setPlayerUuid(uuid);
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public String getAddTime() {
        return addTime;
    }

    public void setAddTime(String addTime) {
        this.addTime = addTime;
    }

    public String getAddedBy() {
        return addedBy;
    }

    public void setAddedBy(String addedBy) {
        this.addedBy = addedBy;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}