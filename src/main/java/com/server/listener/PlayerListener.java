package com.server.listener;

import com.server.AnnouncementCompensationPlugin;
import com.server.data.model.ClaimLog;
import com.server.data.model.Compensation;
import com.server.data.model.Announcement;
import com.server.util.ColorUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 玩家事件监听器
 * 核心职责：
 * 1. 玩家登录：白名单校验、未读公告推送、未领取补偿凭证发放
 * 2. 玩家右键：补偿凭证领取、物品发放
 * 适配 Paper 1.19.2 事件体系 + JDK 17
 */
public class PlayerListener implements Listener {
    // 插件实例（关联数据管理器）
    private final AnnouncementCompensationPlugin plugin;
    // 补偿凭证物品标识（钻石剑，自定义名称）
    private static final String COMPENSATION_ITEM_NAME = ColorUtils.translate("&6&l补偿凭证");
    private static final Material COMPENSATION_ITEM_MATERIAL = Material.DIAMOND_SWORD;

    public PlayerListener(AnnouncementCompensationPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 监听玩家登录事件（PlayerJoinEvent）
     * 处理：白名单校验、未读公告推送、未领取补偿凭证发放
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        String playerUUID = player.getUniqueId().toString();
        String playerName = player.getName();

        // 1. 白名单校验（若启用白名单）
        if (plugin.getDataManager().isWhitelistEnabled()) {
            if (!plugin.getDataManager().isPlayerInWhitelist(playerUUID)) {
                // 踢出非白名单玩家
                player.kick(ColorUtils.toComponent("&c你不在服务器白名单中！"));
                plugin.getLogger().info("非白名单玩家 " + playerName + "(" + playerUUID + ") 尝试登录，已踢出");
                return;
            }
        }

        // 2. 推送未读公告
        pushUnreadAnnouncements(player, playerUUID);

        // 3. 发放未领取补偿的凭证物品
        giveUnclaimedCompensationItems(player, playerUUID);

        plugin.getLogger().info("玩家 " + playerName + " 登录成功，已推送未读公告+未领取补偿凭证");
    }

    /**
     * 监听玩家右键事件（PlayerInteractEvent）
     * 处理：右键补偿凭证领取对应补偿
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player player = e.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // 校验是否是补偿凭证
        if (!isCompensationItem(item)) {
            return;
        }

        // 取消默认右键行为（避免钻石剑攻击/交互）
        e.setCancelled(true);

        String playerUUID = player.getUniqueId().toString();
        String playerName = player.getName();

        // 遍历所有未领取的补偿
        List<Compensation> unclaimedCompensations = plugin.getDataManager().getAllCompensations().stream()
                .filter(comp -> !comp.isClaimed(playerUUID))
                .toList();

        if (unclaimedCompensations.isEmpty()) {
            player.sendMessage(ColorUtils.toComponent("&c你没有未领取的补偿！"));
            return;
        }

        // 发放补偿物品（默认10个钻石，可自定义）
        for (Compensation comp : unclaimedCompensations) {
            // 1. 发放物品（示例：钻石*10，可根据comp.getDescription()解析自定义物品）
            ItemStack reward = new ItemStack(Material.DIAMOND, 10);
            player.getInventory().addItem(reward);

            // 2. 标记补偿已领取
            comp.markClaimed(playerUUID);
            plugin.getDataManager().saveCompensation(comp);

            // 3. 记录领取日志
            ClaimLog log = new ClaimLog(playerName, playerUUID, comp.getId());
            plugin.getDataManager().addClaimLog(log);

            // 4. 发送领取成功提示
            player.sendMessage(ColorUtils.toComponent("&a你已领取补偿：" + comp.getName() + " - " + comp.getDescription()));
        }

        // 移除手中的补偿凭证
        item.setAmount(item.getAmount() - 1);
        player.getInventory().setItemInMainHand(item);

        plugin.getLogger().info("玩家 " + playerName + " 领取了 " + unclaimedCompensations.size() + " 个补偿");
    }

    // ====================== 私有辅助方法 ======================
    /**
     * 推送玩家未读公告
     */
    private void pushUnreadAnnouncements(Player player, String playerUUID) {
        List<Announcement> unreadAnnouncements = plugin.getDataManager().getAllAnnouncements().stream()
                .filter(ann -> ann.isSent() && !ann.getReadStatus().getOrDefault(playerUUID, false))
                .toList();

        if (unreadAnnouncements.isEmpty()) {
            return;
        }

        // 发送公告标题
        player.sendMessage(ColorUtils.toComponent("&6&l【未读公告】(" + unreadAnnouncements.size() + ")"));

        // 发送每条公告内容
        for (Announcement ann : unreadAnnouncements) {
            player.sendMessage(ColorUtils.toComponent(ann.getContent()));
            // 标记为已读
            ann.getReadStatus().put(playerUUID, true);
            plugin.getDataManager().saveAnnouncement(ann);
        }
    }

    /**
     * 发放未领取补偿的凭证物品（仅当玩家背包无凭证时发放）
     */
    private void giveUnclaimedCompensationItems(Player player, String playerUUID) {
        // 检查是否有未领取的补偿
        boolean hasUnclaimed = plugin.getDataManager().getAllCompensations().stream()
                .anyMatch(comp -> !comp.isClaimed(playerUUID));

        if (!hasUnclaimed) {
            return;
        }

        // 检查背包是否已有补偿凭证
        boolean hasCompItem = player.getInventory().getContents() != null &&
                java.util.Arrays.stream(player.getInventory().getContents())
                        .filter(item -> item != null && item.getType() != Material.AIR)
                        .anyMatch(this::isCompensationItem);

        if (hasCompItem) {
            return;
        }

        // 创建补偿凭证物品
        ItemStack compItem = new ItemStack(COMPENSATION_ITEM_MATERIAL);
        ItemMeta meta = compItem.getItemMeta();
        if (meta != null) {
            // 设置物品名称
            meta.displayName(ColorUtils.toComponent(COMPENSATION_ITEM_NAME));
            // 设置物品 lore
            List<Component> lore = new ArrayList<>();
            lore.add(ColorUtils.toComponent("&7右键领取所有未领取的补偿"));
            meta.lore(lore);
            compItem.setItemMeta(meta);
        }

        // 发放到玩家背包
        player.getInventory().addItem(compItem);
        player.sendMessage(ColorUtils.toComponent("&a你有未领取的补偿，已发放【补偿凭证】到背包（右键领取）"));
    }

    /**
     * 判断物品是否是补偿凭证
     */
    private boolean isCompensationItem(ItemStack item) {
        if (item == null || item.getType() != COMPENSATION_ITEM_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName() && meta.displayName().equals(ColorUtils.toComponent(COMPENSATION_ITEM_NAME));
    }
}