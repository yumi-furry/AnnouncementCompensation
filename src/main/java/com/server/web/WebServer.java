package com.server.web;

import com.server.AnnouncementCompensationPlugin;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;


/**
 * Web服务器核心类（兼容Undertow 2.2.19.Final）
 * 支持双端口配置：管理员面板和玩家面板
 */
public class WebServer {
    private final AnnouncementCompensationPlugin plugin;
    private Undertow adminServer;
    private Undertow playerServer;

    public WebServer(AnnouncementCompensationPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 启动管理员面板服务器
     * @param port 管理员面板端口
     */
    public void startAdminServer(int port) {
        try {
            // 1. 加载静态资源
            ClassPathResourceManager resourceManager = new ClassPathResourceManager(
                    plugin.getClass().getClassLoader(), "web/"
            );

            // 2. 配置静态资源处理器
            ResourceHandler resourceHandler = Handlers.resource(resourceManager)
                    .setDirectoryListingEnabled(false)
                    .addWelcomeFiles("admin.html");

            // 3. 配置管理员路由（包含所有功能）
            PathHandler pathHandler = Handlers.path()
                    .addPrefixPath("/", resourceHandler)
                    .addPrefixPath("/api/login", plugin.getLoginHandler())
                    .addPrefixPath("/api/announcement", plugin.getAnnouncementHandler())
                    .addPrefixPath("/api/compensation", plugin.getCompensationHandler())
                    .addPrefixPath("/api/whitelist", plugin.getWhitelistHandler())
                    .addPrefixPath("/api/log", plugin.getLogHandler())
                    .addPrefixPath("/api/user", plugin.getUserHandler())
                    .addPrefixPath("/api/map", plugin.getMapHandler())
                    .addPrefixPath("/api/server", plugin.getServerHandler());

            // 4. 启动管理员服务器
            adminServer = Undertow.builder()
                    .addHttpListener(port, "0.0.0.0")
                    .setHandler(pathHandler)
                    .setIoThreads(4)
                    .setWorkerThreads(8)
                    .build();

            adminServer.start();
            plugin.getLogger().info("✅ 管理员Web面板启动成功，端口：" + port);
            plugin.getLogger().info("🌐 管理员访问地址：http://服务器IP:" + port);
        } catch (Exception e) {
            plugin.getLogger().severe("❌ 管理员Web面板启动失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 启动玩家面板服务器
     * @param port 玩家面板端口
     */
    public void startPlayerServer(int port) {
        try {
            // 1. 加载静态资源
            ClassPathResourceManager resourceManager = new ClassPathResourceManager(
                    plugin.getClass().getClassLoader(), "web/"
            );

            // 2. 配置静态资源处理器
            ResourceHandler resourceHandler = Handlers.resource(resourceManager)
                    .setDirectoryListingEnabled(false)
                    .addWelcomeFiles("player.html");

            // 3. 配置玩家路由（仅包含玩家可访问的功能）
            PathHandler pathHandler = Handlers.path()
                    .addPrefixPath("/", resourceHandler)
                    .addPrefixPath("/api/login", plugin.getLoginHandler())
                    .addPrefixPath("/api/announcement", plugin.getAnnouncementHandler())
                    .addPrefixPath("/api/compensation", plugin.getCompensationHandler())
                    .addPrefixPath("/api/map", plugin.getMapHandler())
                    .addPrefixPath("/api/user", plugin.getUserHandler());

            // 4. 启动玩家服务器
            playerServer = Undertow.builder()
                    .addHttpListener(port, "0.0.0.0")
                    .setHandler(pathHandler)
                    .setIoThreads(4)
                    .setWorkerThreads(8)
                    .build();

            playerServer.start();
            plugin.getLogger().info("✅ 玩家Web面板启动成功，端口：" + port);
            plugin.getLogger().info("🌐 玩家访问地址：http://服务器IP:" + port);
        } catch (Exception e) {
            plugin.getLogger().severe("❌ 玩家Web面板启动失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 停止所有Web服务器
     */
    public void stop() {
        if (adminServer != null) {
            adminServer.stop();
            plugin.getLogger().info("✅ 管理员Web面板已停止");
        }
        if (playerServer != null) {
            playerServer.stop();
            plugin.getLogger().info("✅ 玩家Web面板已停止");
        }
    }

    public AnnouncementCompensationPlugin getPlugin() {
        return plugin;
    }
}