package com.server.command;

import com.server.AnnouncementCompensationPlugin;
import com.server.data.model.User;
import com.server.data.model.Compensation;
import com.server.util.ColorUtils;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * 游戏内命令处理器
 * 处理 /login 和 /tpa 等命令
 */
public class CommandHandler implements CommandExecutor {
    private final AnnouncementCompensationPlugin plugin;
    // 存储待处理的TPA请求
    private final Map<String, String> tpaRequests = new HashMap<>();

    public CommandHandler(AnnouncementCompensationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.toComponent("&c该命令只能在游戏内使用！"));
            return true;
        }

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("login")) {
            return handleLogin(player, args);
        } else if (command.getName().equalsIgnoreCase("tpa")) {
            return handleTPA(player, args);
        } else if (command.getName().equalsIgnoreCase("tpaccept")) {
            return handleTPAccept(player, args);
        } else if (command.getName().equalsIgnoreCase("tpdeny")) {
            return handleTPDeny(player, args);
        } else if (command.getName().equalsIgnoreCase("compensation") || command.getName().equalsIgnoreCase("comp")) {
            return handleCompensation(player, args);
        }

        return false;
    }

    /**
     * 处理 /login [账户名称] [账户密码] 命令
     */
    private boolean handleLogin(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage(ColorUtils.toComponent("&c用法: /login <账户名称> <账户密码>"));
            return true;
        }

        String username = args[0];
        String password = args[1];
        String playerUUID = player.getUniqueId().toString();
        String playerName = player.getName();

        // 查找用户
        User user = plugin.getDataManager().getUserByUsername(username);
        if (user == null) {
            player.sendMessage(ColorUtils.toComponent("&c账户不存在！"));
            plugin.getLogger().info("玩家 " + playerName + " 尝试登录不存在的账户: " + username);
            return true;
        }

        // 验证密码
        if (!user.verifyPassword(password)) {
            player.sendMessage(ColorUtils.toComponent("&c密码错误！"));
            plugin.getLogger().info("玩家 " + playerName + " 登录账户 " + username + " 密码错误");
            return true;
        }

        // 绑定游戏角色（如果尚未绑定）
        if (!user.isGameRoleBound()) {
            user.setGameUUID(playerUUID);
            user.setGameRoleBound(true);
            plugin.getDataManager().saveUser(user);
            plugin.getLogger().info("玩家 " + playerName + " 成功绑定账户: " + username);
            player.sendMessage(ColorUtils.toComponent("&a账户绑定成功！"));
        }

        // 验证游戏角色是否匹配
        if (!user.getGameUUID().equals(playerUUID)) {
            player.sendMessage(ColorUtils.toComponent("&c该账户已绑定其他游戏角色！"));
            plugin.getLogger().info("玩家 " + playerName + " 尝试使用账户 " + username + " 登录，但账户已绑定其他角色");
            return true;
        }

        // 登录成功
        player.sendMessage(ColorUtils.toComponent("&a登录成功！"));
        plugin.getLogger().info("玩家 " + playerName + " 成功登录账户: " + username);
        return true;
    }

    /**
     * 处理 /tpa [A] [B] 命令
     * A要tp到B，需要B同意（如果A是管理员则不需要）
     */
    private boolean handleTPA(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage(ColorUtils.toComponent("&c用法: /tpa <玩家A> <玩家B>"));
            return true;
        }

        Player playerA = Bukkit.getPlayer(args[0]);
        Player playerB = Bukkit.getPlayer(args[1]);

        if (playerA == null) {
            player.sendMessage(ColorUtils.toComponent("&c玩家 " + args[0] + " 不在线！"));
            return true;
        }

        if (playerB == null) {
            player.sendMessage(ColorUtils.toComponent("&c玩家 " + args[1] + " 不在线！"));
            return true;
        }

        // 如果执行者是管理员，则直接TP
        if (player.hasPermission("ac.web.*")) {
            playerA.teleport(playerB.getLocation());
            playerA.sendMessage(ColorUtils.toComponent("&a管理员已将你传送到 " + playerB.getName() + " 的位置！"));
            playerB.sendMessage(ColorUtils.toComponent("&a管理员已将 " + playerA.getName() + " 传送到你的位置！"));
            plugin.getLogger().info("管理员 " + player.getName() + " 执行TPA命令，将 " + playerA.getName() + " 传送到 " + playerB.getName() + " 的位置");
            return true;
        }

        // 普通玩家需要B同意
        String key = playerA.getName() + ":" + playerB.getName();
        tpaRequests.put(key, playerA.getName());

        playerA.sendMessage(ColorUtils.toComponent("&a已发送传送请求给 " + playerB.getName() + "，等待对方同意！"));
        playerB.sendMessage(ColorUtils.toComponent("&a玩家 " + playerA.getName() + " 请求传送到你的位置！"));
        playerB.sendMessage(ColorUtils.toComponent("&a使用 /tpaccept " + playerA.getName() + " 同意，或 /tpdeny " + playerA.getName() + " 拒绝"));
        plugin.getLogger().info("玩家 " + playerA.getName() + " 请求传送到 " + playerB.getName() + " 的位置，等待对方同意");

        return true;
    }

    /**
     * 处理 /tpaccept [玩家A] 命令
     */
    private boolean handleTPAccept(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage(ColorUtils.toComponent("&c用法: /tpaccept <玩家A>"));
            return true;
        }

        String playerAName = args[0];
        Player playerA = Bukkit.getPlayer(playerAName);
        String playerBName = player.getName();
        String key = playerAName + ":" + playerBName;

        if (playerA == null) {
            player.sendMessage(ColorUtils.toComponent("&c玩家 " + playerAName + " 不在线！"));
            return true;
        }

        if (!tpaRequests.containsKey(key)) {
            player.sendMessage(ColorUtils.toComponent("&c没有来自 " + playerAName + " 的传送请求！"));
            return true;
        }

        // 同意传送请求
        playerA.teleport(player.getLocation());
        playerA.sendMessage(ColorUtils.toComponent("&a玩家 " + playerBName + " 已同意你的传送请求！"));
        player.sendMessage(ColorUtils.toComponent("&a已同意玩家 " + playerAName + " 的传送请求！"));
        plugin.getLogger().info("玩家 " + playerBName + " 同意了 " + playerAName + " 的传送请求");

        // 移除请求
        tpaRequests.remove(key);
        return true;
    }

    /**
     * 处理 /tpdeny [玩家A] 命令
     */
    private boolean handleTPDeny(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage(ColorUtils.toComponent("&c用法: /tpdeny <玩家A>"));
            return true;
        }

        String playerAName = args[0];
        Player playerA = Bukkit.getPlayer(playerAName);
        String playerBName = player.getName();
        String key = playerAName + ":" + playerBName;

        if (playerA == null) {
            player.sendMessage(ColorUtils.toComponent("&c玩家 " + playerAName + " 不在线！"));
            return true;
        }

        if (!tpaRequests.containsKey(key)) {
            player.sendMessage(ColorUtils.toComponent("&c没有来自 " + playerAName + " 的传送请求！"));
            return true;
        }

        // 拒绝传送请求
        playerA.sendMessage(ColorUtils.toComponent("&c玩家 " + playerBName + " 拒绝了你的传送请求！"));
        player.sendMessage(ColorUtils.toComponent("&c已拒绝玩家 " + playerAName + " 的传送请求！"));
        plugin.getLogger().info("玩家 " + playerBName + " 拒绝了 " + playerAName + " 的传送请求");

        // 移除请求
        tpaRequests.remove(key);
        return true;
    }

    /**
     * 处理 /compensation [list/claim] 命令
     */
    private boolean handleCompensation(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(ColorUtils.toComponent("&c用法: /compensation list - 查看未领取补偿"));
            player.sendMessage(ColorUtils.toComponent("&c用法: /compensation claim - 领取补偿"));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equals("list")) {
            return handleCompensationList(player);
        } else if (subCommand.equals("claim")) {
            return handleCompensationClaim(player);
        } else {
            player.sendMessage(ColorUtils.toComponent("&c未知子命令！请使用 list 或 claim"));
            return true;
        }
    }

    /**
     * 处理 /compensation list 命令 - 查看未领取补偿
     */
    private boolean handleCompensationList(Player player) {
        String playerUUID = player.getUniqueId().toString();
        List<Compensation> unclaimedCompensations = plugin.getDataManager().getAllCompensations().stream()
                .filter(comp -> !comp.isClaimed(playerUUID))
                .toList();

        if (unclaimedCompensations.isEmpty()) {
            player.sendMessage(ColorUtils.toComponent("&a你没有未领取的补偿！"));
            return true;
        }

        player.sendMessage(ColorUtils.toComponent("&6&l【未领取补偿列表】"));
        for (Compensation comp : unclaimedCompensations) {
            player.sendMessage(ColorUtils.toComponent("&a" + comp.getName() + "：" + comp.getDescription()));
            if (!comp.getItems().isEmpty()) {
                player.sendMessage(ColorUtils.toComponent("&7物品列表："));
                for (Compensation.CompensationItem item : comp.getItems()) {
                    String itemInfo = item.getAmount() + "个 " + item.getMaterial();
                    if (item.getCustomName() != null) {
                        itemInfo += " (" + item.getCustomName() + ")";
                    }
                    player.sendMessage(ColorUtils.toComponent("&7- " + itemInfo));
                }
            }
        }
        player.sendMessage(ColorUtils.toComponent("&a使用 /compensation claim 领取所有补偿"));
        return true;
    }

    /**
     * 处理 /compensation claim 命令 - 领取补偿
     */
    private boolean handleCompensationClaim(Player player) {
        String playerUUID = player.getUniqueId().toString();
        List<Compensation> unclaimedCompensations = plugin.getDataManager().getAllCompensations().stream()
                .filter(comp -> !comp.isClaimed(playerUUID))
                .toList();

        if (unclaimedCompensations.isEmpty()) {
            player.sendMessage(ColorUtils.toComponent("&c你没有未领取的补偿！"));
            return true;
        }

        // 发放补偿物品
        for (Compensation comp : unclaimedCompensations) {
            // 1. 发放定义的补偿物品
            for (Compensation.CompensationItem compItem : comp.getItems()) {
                try {
                    org.bukkit.Material material = org.bukkit.Material.valueOf(compItem.getMaterial());
                    org.bukkit.inventory.ItemStack reward = new org.bukkit.inventory.ItemStack(material, compItem.getAmount());
                    
                    // 设置自定义名称和描述
                    org.bukkit.inventory.meta.ItemMeta meta = reward.getItemMeta();
                    if (meta != null) {
                        if (compItem.getCustomName() != null) {
                            meta.displayName(ColorUtils.toComponent(compItem.getCustomName()));
                        }
                        if (compItem.getLore() != null && !compItem.getLore().isEmpty()) {
                            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
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
            com.server.data.model.ClaimLog log = new com.server.data.model.ClaimLog(player.getName(), playerUUID, comp.getIdString());
            plugin.getDataManager().addClaimLog(log);
        }

        player.sendMessage(ColorUtils.toComponent("&a已成功领取 " + unclaimedCompensations.size() + " 个补偿！"));
        plugin.getLogger().info("玩家 " + player.getName() + " 通过命令领取了 " + unclaimedCompensations.size() + " 个补偿");
        return true;
    }
}
