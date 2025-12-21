package com.server.command;

import com.server.AnnouncementCompensationPlugin;
import com.server.util.AbsoluteIDManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * VTCommand - 玩家获取绝对ID的指令
 * 玩家输入 /vtc 可以获取自己的角色绝对ID
 * 用于面板账号绑定游戏角色
 */
public class VTCommand implements CommandExecutor {
    private final AnnouncementCompensationPlugin plugin;
    private final AbsoluteIDManager absoluteIDManager;

    public VTCommand(AnnouncementCompensationPlugin plugin, AbsoluteIDManager absoluteIDManager) {
        this.plugin = plugin;
        this.absoluteIDManager = absoluteIDManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 仅允许玩家执行此命令
        if (!(sender instanceof Player)) {
            sender.sendMessage("此指令仅玩家可用！");
            return true;
        }

        Player player = (Player) sender;
        absoluteIDManager.sendAbsoluteIDToPlayer(player);
        return true;
    }
}
