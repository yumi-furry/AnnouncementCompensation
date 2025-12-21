package com.server.data.model;

import com.google.gson.annotations.SerializedName;
import com.server.util.TimeUtils;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 补偿模型类
 * 对应JSON结构：{"id":"xxx","name":"新手补偿","description":"&6钻石*10","createTime":"2025-12-19 09:00","claimStatus":{"玩家UUID":true},"items":[{"material":"DIAMOND","amount":10}]}
 */
public class Compensation {
    // 数据库ID（transient：JSON序列化/反序列化时忽略）
    private transient int id;
    // 补偿唯一ID（UUID生成）
    @SerializedName("id")
    private String compensationId;
    // 补偿名称
    private String title;
    // 补偿说明（支持&颜色符）
    private String description;
    // 创建时间
    private String createTime;
    // 更新时间
    private String updatedAt;
    // 作者
    private String author;
    // 玩家领取状态（Key：玩家UUID，Value：是否领取）
    private Map<String, Boolean> claimStatus = new HashMap<>();
    // 补偿物品列表
    private List<CompensationItem> items = new ArrayList<>();

    // 无参构造
    public Compensation() {}

    // 有参构造
    public Compensation(String title, String description) {
        this.title = title;
        this.description = description;
        this.createTime = TimeUtils.getCurrentTimeStr();
        this.updatedAt = TimeUtils.getCurrentTimeStr();
        this.items = new ArrayList<>();
    }

    // 数据库构造方法
    public Compensation(int id, String title, String description, String author, String updatedAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.author = author;
        this.updatedAt = updatedAt;
        this.createTime = TimeUtils.getCurrentTimeStr();
        this.items = new ArrayList<>();
    }

    /**
     * 补偿物品内部类
     * 表示补偿中的单个物品
     */
    public static class CompensationItem {
        // 物品类型
        private String material;
        // 物品数量
        private int amount;
        // 物品自定义名称（可选）
        private String customName;
        // 物品描述（可选）
        private List<String> lore;

        // 无参构造
        public CompensationItem() {}

        // 有参构造
        public CompensationItem(String material, int amount) {
            this.material = material;
            this.amount = amount;
        }

        // Getter/Setter
        public String getMaterial() {
            return material;
        }

        public void setMaterial(String material) {
            this.material = material;
        }

        public int getAmount() {
            return amount;
        }

        public void setAmount(int amount) {
            this.amount = amount;
        }

        public String getCustomName() {
            return customName;
        }

        public void setCustomName(String customName) {
            this.customName = customName;
        }

        public List<String> getLore() {
            return lore;
        }

        public void setLore(List<String> lore) {
            this.lore = lore;
        }
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
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    // 向后兼容的方法
    public String getIdString() {
        return compensationId;
    }

    public void setId(String id) {
        this.compensationId = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    // 向后兼容的别名方法
    public String getName() {
        return getTitle();
    }

    public void setName(String name) {
        setTitle(name);
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

    public List<CompensationItem> getItems() {
        return items;
    }

    public void setItems(List<CompensationItem> items) {
        this.items = items != null ? items : new ArrayList<>();
    }

    // 便捷方法：支持从字符串列表设置物品（向后兼容）
    public void setItemsFromStrings(List<String> itemStrings) {
        this.items = new ArrayList<>();
        if (itemStrings != null) {
            for (String itemStr : itemStrings) {
                CompensationItem item = new CompensationItem();
                item.setMaterial(itemStr);
                item.setAmount(1);
                this.items.add(item);
            }
        }
    }

    // 获取物品字符串列表（用于数据库存储）
    public List<String> getItemsAsStrings() {
        List<String> itemStrings = new ArrayList<>();
        if (items != null) {
            for (CompensationItem item : items) {
                itemStrings.add(item.getMaterial());
            }
        }
        return itemStrings;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
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
}