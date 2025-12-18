package com.server;

import com.server.data.DataManager;
import com.server.web.WebServer;
import com.server.web.handler.*;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 插件主类（必须继承JavaPlugin）
 * 负责初始化所有组件、启动Web服务器、管理数据
 */
public class AnnouncementCompensationPlugin extends JavaPlugin {
    // 1. 全局单例
    private static AnnouncementCompensationPlugin instance;
    
    // 2. 核心组件
    private DataManager dataManager;
    private WebServer webServer;
    
    // 3. Handler实例
    private LoginHandler loginHandler;
    private AnnouncementHandler announcementHandler;
    private CompensationHandler compensationHandler;
    private WhitelistHandler whitelistHandler;
    private LogHandler logHandler;

    @Override
    public void onEnable() {
        instance = this;

        // 将默认配置文件写入插件数据目录（首次运行会生成 plugins/AnnouncementCompensation/config.yml）
        saveDefaultConfig();

        // 初始化数据管理器并加载数据
        this.dataManager = new DataManager(this);
        this.dataManager.loadAllData();
        // 确保存在默认管理员（从config读取或创建默认）
        this.dataManager.ensureDefaultAdminFromConfig();

        // 初始化 Handler（使用已经加载的 dataManager）
        this.loginHandler = new LoginHandler(this);
        this.announcementHandler = new AnnouncementHandler(this);
        this.compensationHandler = new CompensationHandler(this);
        this.whitelistHandler = new WhitelistHandler(this);
        this.logHandler = new LogHandler(this);

        // 启动 Web 服务器，端口从 config.yml 读取，默认 8080
        this.webServer = new WebServer(this);
        int port = getConfig().getInt("web.port", 8080);
        this.webServer.start(port);

        getLogger().info("✅ 公告&补偿管理插件启动成功！");
    }

    @Override
    public void onDisable() {
        // 停止 Web 服务器
        if (this.webServer != null) {
            this.webServer.stop();
        }
        // 保存数据
        if (this.dataManager != null) {
            this.dataManager.saveAllData();
        }
        getLogger().info("✅ 公告&补偿管理插件已停止！");
    }

    public static AnnouncementCompensationPlugin getInstance() {
        return instance;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public LoginHandler getLoginHandler() {
        return loginHandler;
    }

    public AnnouncementHandler getAnnouncementHandler() {
        return announcementHandler;
    }

    public CompensationHandler getCompensationHandler() {
        return compensationHandler;
    }

    public WhitelistHandler getWhitelistHandler() {
        return whitelistHandler;
    }

    public LogHandler getLogHandler() {
        return logHandler;
    }
}