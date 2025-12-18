package com.server.web;

import com.server.AnnouncementCompensationPlugin;
import com.server.web.handler.*;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;

/**
 * Web服务器核心类（兼容Undertow 2.2.19.Final）
 */
public class WebServer {
    private final AnnouncementCompensationPlugin plugin;
    private Undertow server;

    public WebServer(AnnouncementCompensationPlugin plugin) {
        this.plugin = plugin;
    }

    public void start(int port) {
        try {
            // 1. 加载静态资源（兼容所有版本）
            ClassPathResourceManager resourceManager = new ClassPathResourceManager(
                    plugin.getClass().getClassLoader(), "web/"
            );

            // 2. 配置静态资源处理器（核心修复：放弃setDefaultFile）
            ResourceHandler resourceHandler = Handlers.resource(resourceManager)
                    .setDirectoryListingEnabled(false)
                    // 直接通过路由匹配默认页面（兼容所有Undertow版本）
                    .addWelcomeFiles("index.html");

            // 3. 配置路由 —— 使用插件中已创建的 Handler 实例，避免跨实例状态不一致
            PathHandler pathHandler = Handlers.path()
                    .addPrefixPath("/", resourceHandler)
                    .addPrefixPath("/api/login", plugin.getLoginHandler())
                    .addPrefixPath("/api/announcement", plugin.getAnnouncementHandler())
                    .addPrefixPath("/api/compensation", plugin.getCompensationHandler())
                    .addPrefixPath("/api/whitelist", plugin.getWhitelistHandler())
                    .addPrefixPath("/api/log", plugin.getLogHandler());

            // 4. 启动服务器
            server = Undertow.builder()
                    .addHttpListener(port, "0.0.0.0")
                    .setHandler(pathHandler)
                    .setIoThreads(4)
                    .setWorkerThreads(8)
                    .build();

            server.start();
            plugin.getLogger().info("✅ Web服务器启动成功，端口：" + port);
            plugin.getLogger().info("🌐 访问地址：http://服务器IP:" + port);
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Web服务器启动失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    public void stop() {
        if (server != null) {
            server.stop();
            plugin.getLogger().info("✅ Web服务器已停止");
        }
    }

    public AnnouncementCompensationPlugin getPlugin() {
        return plugin;
    }
}