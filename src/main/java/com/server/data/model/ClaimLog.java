package com.server.data.model;

import com.server.util.TimeUtils;

/**
 * 补偿领取日志模型类
 * 对应JSON结构：{"id":"xxx","playerName":"玩家名","playerUUID":"玩家UUID","compensationId":"补偿ID","claimTime":"2025-12-19 10:00"}
 */
public class ClaimLog {
    // 数据库ID
    private int id;
    // 日志唯一ID（UUID生成）
    private String logId;
    // 玩家名称
    private String playerName;
    // 玩家UUID
    private String playerUUID;
    // 领取的补偿ID
    private String compensationId;
    // 补偿ID（数据库）
    private int compensationInt;
    // 领取时间
    private String claimTime;

    // 无参构造
    public ClaimLog() {}

    // 有参构造
    public ClaimLog(String playerName, String playerUUID, String compensationId) {
        this.playerName = playerName;
        this.playerUUID = playerUUID;
        this.compensationId = compensationId;
        this.claimTime = TimeUtils.getCurrentTimeStr();
    }

    // 数据库构造方法
    public ClaimLog(int id, String playerName, String playerUUID, int compensationInt) {
        this.id = id;
        this.playerName = playerName;
        this.playerUUID = playerUUID;
        this.compensationInt = compensationInt;
        this.claimTime = TimeUtils.getCurrentTimeStr();
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
        return logId;
    }

    public void setId(String id) {
        this.logId = id;
    }

    public String getPlayerUuid() {
        return playerUUID;
    }

    public void setPlayerUuid(String playerUuid) {
        this.playerUUID = playerUuid;
    }

    public int getCompensationIdInt() {
        return compensationInt;
    }

    public void setCompensationId(int compensationId) {
        this.compensationInt = compensationId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public String getPlayerUUID() {
        return playerUUID;
    }

    public void setPlayerUUID(String playerUUID) {
        this.playerUUID = playerUUID;
    }

    public String getCompensationId() {
        return compensationId;
    }

    public void setCompensationId(String compensationId) {
        this.compensationId = compensationId;
    }

    public String getClaimTime() {
        return claimTime;
    }

    public void setClaimTime(String claimTime) {
        this.claimTime = claimTime;
    }
}