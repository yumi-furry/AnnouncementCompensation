package com.server.listener;

import com.server.AnnouncementCompensationPlugin;
import com.server.util.ColorUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.util.CachedServerIcon;

import java.io.File;

/**
 * 服务器列表监听器
 * 负责在玩家查看服务器列表时应用自定义MOTD和服务器图标
 */
public class ServerListListener implements Listener {
    private final AnnouncementCompensationPlugin plugin;
    private CachedServerIcon serverIcon;
    private long lastIconLoadTime = 0;
    private static final long ICON_CACHE_TIME = 5000; // 5秒缓存

    public ServerListListener(AnnouncementCompensationPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 监听服务器列表Ping事件
     * 应用自定义服务器图标
     */
    @EventHandler
    public void onServerListPing(ServerListPingEvent event) {
        try {
            // 获取配置
            boolean iconEnabled = plugin.getConfig().getBoolean("server.icon.enable", false);
            
            // 应用自定义服务器图标
            if (iconEnabled) {
                CachedServerIcon icon = loadServerIcon();
                if (icon != null) {
                    event.setServerIcon(icon);
                    plugin.getLogger().info("已应用自定义服务器图标");
                }
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("应用服务器列表配置时出错：" + e.getMessage());
        }
    }

    /**
     * 加载服务器图标（带缓存）
     */
    private CachedServerIcon loadServerIcon() {
        // 检查缓存
        long currentTime = System.currentTimeMillis();
        if (serverIcon != null && (currentTime - lastIconLoadTime) < ICON_CACHE_TIME) {
            return serverIcon;
        }
        
        try {
            String iconPath = plugin.getConfig().getString("server.icon.path", "");
            if (iconPath == null || iconPath.trim().isEmpty()) {
                return null;
            }
            
            // 尝试从不同路径加载图标
            File iconFile = new File(iconPath);
            if (!iconFile.exists()) {
                // 尝试从插件目录加载
                iconFile = new File(plugin.getDataFolder().getParentFile(), iconPath);
            }
            if (!iconFile.exists()) {
                // 尝试从服务器根目录加载
                iconFile = new File(iconPath);
            }
            
            if (iconFile.exists() && iconFile.canRead()) {
                // 使用Bukkit API加载服务器图标
                serverIcon = Bukkit.loadServerIcon(iconFile);
                if (serverIcon != null) {
                    lastIconLoadTime = currentTime;
                    return serverIcon;
                }
            } else {
                plugin.getLogger().warning("服务器图标文件不存在：" + iconFile.getAbsolutePath());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("加载服务器图标失败：" + e.getMessage());
        }
        
        return null;
    }

    /**
     * 清除图标缓存（用于配置更新后刷新）
     */
    public void clearIconCache() {
        this.serverIcon = null;
        this.lastIconLoadTime = 0;
    }

    /**
     * 重新加载服务器图标
     */
    public void reloadServerIcon() {
        clearIconCache();
        plugin.getLogger().info("服务器图标缓存已清除，将重新加载");
    }
}