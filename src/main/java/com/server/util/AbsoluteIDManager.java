package com.server.util;

import com.server.AnnouncementCompensationPlugin;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 绝对ID管理器
 * 负责生成、存储和验证玩家角色的绝对ID
 * 绝对ID格式：UUID + 时间戳，确保唯一性
 */
public class AbsoluteIDManager {
    private final AnnouncementCompensationPlugin plugin;
    private final Map<String, String> playerIdMap; // 玩家UUID -> 绝对ID

    public AbsoluteIDManager(AnnouncementCompensationPlugin plugin) {
        this.plugin = plugin;
        this.playerIdMap = new ConcurrentHashMap<>();
    }

    /**
     * 为玩家生成绝对ID
     * @param playerUUID 玩家UUID
     * @return 唯一的绝对ID
     */
    public String generateAbsoluteID(String playerUUID) {
        // 格式：UUID-时间戳
        String absoluteID = UUID.randomUUID().toString() + "-" + System.currentTimeMillis();
        playerIdMap.put(playerUUID, absoluteID);
        return absoluteID;
    }

    /**
     * 获取玩家的绝对ID，如果不存在则生成新的
     * @param playerUUID 玩家UUID
     * @return 绝对ID
     */
    public String getOrGenerateAbsoluteID(String playerUUID) {
        return playerIdMap.computeIfAbsent(playerUUID, this::generateAbsoluteID);
    }

    /**
     * 获取玩家的绝对ID
     * @param playerUUID 玩家UUID
     * @return 绝对ID，如果不存在则返回null
     */
    public String getAbsoluteID(String playerUUID) {
        return playerIdMap.get(playerUUID);
    }

    /**
     * 通过绝对ID获取玩家UUID
     * @param absoluteID 绝对ID
     * @return 玩家UUID，如果不存在则返回null
     */
    public String getPlayerUUIDByAbsoluteID(String absoluteID) {
        for (Map.Entry<String, String> entry : playerIdMap.entrySet()) {
            if (entry.getValue().equals(absoluteID)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 发送绝对ID给玩家
     * @param player 玩家对象
     */
    public void sendAbsoluteIDToPlayer(Player player) {
        String playerUUID = player.getUniqueId().toString();
        String absoluteID = getOrGenerateAbsoluteID(playerUUID);
        player.sendMessage(ColorUtils.toComponent("&a你的角色绝对ID是: &6" + absoluteID));
        player.sendMessage(ColorUtils.toComponent("&a请复制此ID到面板注册后的绑定弹窗中完成绑定！"));
    }
}
