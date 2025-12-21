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
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
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
    // 补偿凭证物品标识（特殊纸张，自定义名称）
    private static final String COMPENSATION_ITEM_NAME = ColorUtils.translate("&6&l补偿凭证");
    private static final Material COMPENSATION_ITEM_MATERIAL = Material.PAPER;

    public PlayerListener(AnnouncementCompensationPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 监听玩家登录事件（PlayerJoinEvent）
     * 处理：白名单校验、登录提示、未读公告推送、未领取补偿凭证发放
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

        // 2. 提示玩家登录（如果尚未绑定账户）
        boolean hasBoundAccount = plugin.getDataManager().getUsers().stream()
                .anyMatch(user -> user.getGameUUID() != null && user.getGameUUID().equals(playerUUID));

        if (!hasBoundAccount) {
            // 对于内网渗透场景，提供本地访问地址和配置域名两种方式
            int playerPort = plugin.getConfig().getInt("web.player_port", 8081);
            String localUrl = "https://localhost:" + playerPort;
            
            // 获取配置的域名（如果有的话）
            String configDomain = plugin.getConfig().getString("web.domain", "");
            String registerUrl = localUrl;
            
            // 如果配置了域名且不是localhost，则同时提供域名访问方式
            if (!configDomain.isEmpty() && !"localhost".equalsIgnoreCase(configDomain)) {
                String domainUrl = "https://" + configDomain + ":" + playerPort;
                registerUrl = domainUrl + " 或 " + localUrl;
            }
            
            player.sendMessage(ColorUtils.toComponent("&a请使用 /login <账户名称> <账户密码> 登录你的账户！"));
            player.sendMessage(ColorUtils.toComponent("&a如果还没有账户，请先在面板注册：" + registerUrl));
        } else {
            // 已绑定账户，直接登录
            player.sendMessage(ColorUtils.toComponent("&a你已绑定账户，欢迎回来！"));
            // 3. 推送未读公告
            pushUnreadAnnouncements(player, playerUUID);
            // 4. 发放未领取补偿的凭证物品
            giveUnclaimedCompensationItems(player, playerUUID);
        }

        plugin.getLogger().info("玩家 " + playerName + " 登录成功");
    }

    /**
     * 监听玩家右键事件（PlayerInteractEvent）
     * 处理：右键补偿凭证查看补偿原因并领取对应补偿
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player player = e.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // 校验是否是补偿凭证
        if (!isCompensationItem(item)) {
            return;
        }

        // 取消默认右键行为（避免纸张的默认交互）
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

        // 先显示所有未领取的补偿原因
        player.sendMessage(ColorUtils.toComponent("&6&l【未领取补偿列表】"));
        for (Compensation comp : unclaimedCompensations) {
            player.sendMessage(ColorUtils.toComponent("&a" + comp.getName() + "：" + comp.getDescription()));
        }

        // 发放补偿物品
        for (Compensation comp : unclaimedCompensations) {
            // 1. 发放定义的补偿物品
            for (Compensation.CompensationItem compItem : comp.getItems()) {
                try {
                    Material material = Material.valueOf(compItem.getMaterial());
                    ItemStack reward = new ItemStack(material, compItem.getAmount());
                    
                    // 设置自定义名称和描述
                    ItemMeta meta = reward.getItemMeta();
                    if (meta != null) {
                        if (compItem.getCustomName() != null) {
                            meta.displayName(ColorUtils.toComponent(compItem.getCustomName()));
                        }
                        if (compItem.getLore() != null && !compItem.getLore().isEmpty()) {
                            List<Component> lore = new ArrayList<>();
                            for (String line : compItem.getLore()) {
                                lore.add(ColorUtils.toComponent(line));
                            }
                            meta.lore(lore);
                        }
                        reward.setItemMeta(meta);
                    }
                    
                    // 发放到玩家背包
                    player.getInventory().addItem(reward);
                } catch (Exception ex) {
                    plugin.getLogger().warning("发放补偿物品失败：" + ex.getMessage());
                    player.sendMessage(ColorUtils.toComponent("&c发放补偿物品失败：" + compItem.getMaterial()));
                }
            }

            // 2. 标记补偿已领取
            comp.markClaimed(playerUUID);
            plugin.getDataManager().saveCompensation(comp);

            // 3. 记录领取日志
            ClaimLog log = new ClaimLog(playerName, playerUUID, comp.getIdString());
            plugin.getDataManager().addClaimLog(log);
        }

        // 移除手中的补偿凭证
        item.setAmount(item.getAmount() - 1);
        player.getInventory().setItemInMainHand(item);

        player.sendMessage(ColorUtils.toComponent("&a所有补偿已领取完成！"));
        plugin.getLogger().info("玩家 " + playerName + " 领取了 " + unclaimedCompensations.size() + " 个补偿");
    }
    
    /**
     * 监听玩家移动事件（PlayerMoveEvent）
     * 限制未登录玩家移动
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        Player player = e.getPlayer();
        String playerUUID = player.getUniqueId().toString();
        
        // 检查玩家是否已绑定账户
        boolean hasBoundAccount = plugin.getDataManager().getUsers().stream()
                .anyMatch(user -> user.getGameUUID() != null && user.getGameUUID().equals(playerUUID));
        
        if (!hasBoundAccount) {
            e.setCancelled(true);
            player.sendMessage(ColorUtils.toComponent("&c请先使用 /login <账户名称> <账户密码> 登录你的账户！"));
        }
    }
    
    /**
     * 监听玩家打开背包事件（InventoryOpenEvent）
     * 限制未登录玩家打开背包
     */
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player player)) {
            return;
        }
        
        String playerUUID = player.getUniqueId().toString();
        
        // 检查玩家是否已绑定账户
        boolean hasBoundAccount = plugin.getDataManager().getUsers().stream()
                .anyMatch(user -> user.getGameUUID() != null && user.getGameUUID().equals(playerUUID));
        
        if (!hasBoundAccount) {
            e.setCancelled(true);
            player.sendMessage(ColorUtils.toComponent("&c请先使用 /login <账户名称> <账户密码> 登录你的账户！"));
        }
    }

    // ====================== 私有辅助方法 ======================
    /**
     * 推送玩家未读公告
     */
    private void pushUnreadAnnouncements(Player player, String playerUUID) {
        List<Announcement> unreadAnnouncements = plugin.getDataManager().getAllAnnouncements().stream()
                .filter(ann -> ann.isSent() && !ann.getReadStatus().getOrDefault(playerUUID, false))
                .sorted((a1, a2) -> Integer.compare(a2.getPriority(), a1.getPriority())) // 按优先级降序排序
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