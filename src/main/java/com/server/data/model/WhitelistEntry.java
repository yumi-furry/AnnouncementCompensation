package com.server.data.model;

import com.server.util.TimeUtils;

/**
 * 白名单模型类
 * 对应JSON结构：{"uuid":"玩家UUID","playerName":"玩家名","addTime":"2025-12-19 09:00"}
 */
public class WhitelistEntry {
    // 玩家UUID（唯一）
    private String uuid;
    // 玩家名称
    private String playerName;
    // 添加时间
    private String addTime;

    // 无参构造
    public WhitelistEntry() {}

    // 有参构造
    public WhitelistEntry(String uuid, String playerName) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.addTime = TimeUtils.getCurrentTimeStr();
    }

    // ====================== Getter/Setter ======================
    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
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
}