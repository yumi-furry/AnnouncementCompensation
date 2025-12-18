package com.server.data.model;

import com.server.util.TimeUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 补偿模型类
 * 对应JSON结构：{"id":"xxx","name":"新手补偿","description":"&6钻石*10","createTime":"2025-12-19 09:00","claimStatus":{"玩家UUID":true}}
 */
public class Compensation {
    // 补偿唯一ID（UUID生成）
    private String id;
    // 补偿名称
    private String name;
    // 补偿说明（支持&颜色符）
    private String description;
    // 创建时间
    private String createTime;
    // 玩家领取状态（Key：玩家UUID，Value：是否领取）
    private Map<String, Boolean> claimStatus = new HashMap<>();

    // 无参构造
    public Compensation() {}

    // 有参构造
    public Compensation(String name, String description) {
        this.name = name;
        this.description = description;
        this.createTime = TimeUtils.getCurrentTimeStr();
    }

    /**
     * 检查玩家是否已领取该补偿
     */
    public boolean isClaimed(String playerUUID) {
        return claimStatus.containsKey(playerUUID) && claimStatus.get(playerUUID);
    }

    /**
     * 标记玩家已领取
     */
    public void markClaimed(String playerUUID) {
        claimStatus.put(playerUUID, true);
    }

    // ====================== Getter/Setter ======================
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public Map<String, Boolean> getClaimStatus() {
        return claimStatus;
    }

    public void setClaimStatus(Map<String, Boolean> claimStatus) {
        this.claimStatus = claimStatus;
    }
}