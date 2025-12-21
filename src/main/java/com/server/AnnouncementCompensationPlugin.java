package com.server;

import com.server.command.CommandHandler;
import com.server.data.DataManager;
import com.server.database.DatabaseManager;
import com.server.listener.PlayerListener;
import com.server.listener.ServerListListener;
import com.server.util.ColorUtils;
import com.server.web.WebServer;
import com.server.web.handler.*;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * 插件主类（必须继承JavaPlugin）
 * 负责初始化所有组件、启动Web服务器、管理数据
 */
public class AnnouncementCompensationPlugin extends JavaPlugin {
    // 1. 全局单例
    private static AnnouncementCompensationPlugin instance;
    
    // 2. 核心组件
    private DatabaseManager databaseManager;
    private DataManager dataManager;
    private WebServer webServer;
    
    // 3. Handler实例
    private LoginHandler loginHandler;
    private AnnouncementHandler announcementHandler;
    private CompensationHandler compensationHandler;
    private WhitelistHandler whitelistHandler;
    private LogHandler logHandler;
    private UserHandler userHandler;
    private MapHandler mapHandler;
    private ServerHandler serverHandler;
    private CommandHandler commandHandler;

    @Override
    public void onEnable() {
        instance = this;

        // 仅在配置文件不存在时写入默认配置，避免覆盖用户修改的配置
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveDefaultConfig();
            getLogger().info(ColorUtils.translate("&a✅ 已生成默认配置文件"));
        } else {
            getLogger().info(ColorUtils.translate("&a✅ 使用现有配置文件"));
        }
        
        // 检查配置是否为默认值并提示安全问题
        checkDefaultConfig();

        // 1. 初始化数据库管理器
        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.initialize();
        
        // 2. 初始化数据管理器并加载数据
        this.dataManager = new DataManager(this, this.databaseManager);
        this.dataManager.loadAllData();
        // 确保存在默认管理员（从config读取或创建默认）
        this.dataManager.ensureDefaultAdminFromConfig();

        // 初始化 Handler（使用已经加载的 dataManager）
        this.loginHandler = new LoginHandler(this);
        this.announcementHandler = new AnnouncementHandler(this);
        this.compensationHandler = new CompensationHandler(this);
        this.whitelistHandler = new WhitelistHandler(this);
        this.logHandler = new LogHandler(this);
        this.userHandler = new UserHandler(this);
        this.mapHandler = new MapHandler(this);
        this.serverHandler = new ServerHandler(this);
        this.commandHandler = new CommandHandler(this);

        // 注册命令
        getCommand("login").setExecutor(commandHandler);
        getCommand("tpa").setExecutor(commandHandler);
        getCommand("tpaccept").setExecutor(commandHandler);
        getCommand("tpdeny").setExecutor(commandHandler);
        getCommand("compensation").setExecutor(commandHandler);
        getCommand("comp").setExecutor(commandHandler);

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new ServerListListener(this), this);

        // 启动 Web 服务器，读取双端口配置
        this.webServer = new WebServer(this);
        int adminPort = getConfig().getInt("web.admin_port", 8080);
        int playerPort = getConfig().getInt("web.player_port", 8081);
        this.webServer.startAdminServer(adminPort);
        this.webServer.startPlayerServer(playerPort);

        getLogger().info(ColorUtils.translate("&a✅ 公告&补偿管理插件启动成功！"));
        getLogger().info(ColorUtils.translate("&a✅ 命令处理器已初始化，/login 和 /tpa 命令已注册！"));
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
        // 关闭数据库连接池
        if (this.databaseManager != null) {
            this.databaseManager.close();
        }
        getLogger().info(ColorUtils.translate("&a✅ 公告&补偿管理插件已停止！"));
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
    
    public UserHandler getUserHandler() {
        return userHandler;
    }
    
    public MapHandler getMapHandler() {
        return mapHandler;
    }
    
    public ServerHandler getServerHandler() {
        return serverHandler;
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    /**
     * 检查配置是否为默认值并提示安全问题
     * 检测关键配置项是否使用默认值，如果是则输出警告信息
     */
    private void checkDefaultConfig() {
        boolean hasDefaultConfig = false;
        
        // 检查Web管理面板登录信息
        String webUsername = getConfig().getString("web.login.username", "admin");
        String webPassword = getConfig().getString("web.login.password", "");
        String webDomain = getConfig().getString("web.domain", "localhost");
        
        if (webUsername.equals("admin")) {
            getServer().sendMessage(ColorUtils.toComponent("&6⚠️ [警告] 检测到默认管理员账号 'admin' 未修改，存在安全风险，请及时更新配置！"));
            hasDefaultConfig = true;
        }
        
        if (webPassword.equals("$2a$10$xxxxxx") || webPassword.isEmpty()) {
            getServer().sendMessage(ColorUtils.toComponent("&6⚠️ [警告] 检测到默认管理员密码未修改，存在安全风险，请及时更新配置！"));
            hasDefaultConfig = true;
        }
        
        if (webDomain.equals("localhost")) {
            getServer().sendMessage(ColorUtils.toComponent("&6⚠️ [警告] 检测到Web面板域名配置为默认值 'localhost'，请根据实际情况修改！"));
            hasDefaultConfig = true;
        }
        
        // 检查SMTP配置
        boolean smtpEnabled = getConfig().getBoolean("smtp.enable", false);
        String smtpUsername = getConfig().getString("smtp.username", "your_email@gmail.com");
        String smtpPassword = getConfig().getString("smtp.password", "your_app_password");
        String smtpHost = getConfig().getString("smtp.host", "smtp.gmail.com");
        
        if (smtpEnabled) {
            if (smtpUsername.equals("your_email@gmail.com")) {
                getServer().sendMessage(ColorUtils.toComponent("&6⚠️ [警告] 检测到默认SMTP用户名未修改，邮件发送功能可能无法正常工作！"));
                hasDefaultConfig = true;
            }
            
            if (smtpPassword.equals("your_app_password")) {
                getServer().sendMessage(ColorUtils.toComponent("&6⚠️ [警告] 检测到默认SMTP密码未修改，邮件发送功能可能无法正常工作！"));
                hasDefaultConfig = true;
            }
            
            if (smtpHost.equals("smtp.gmail.com")) {
                getServer().sendMessage(ColorUtils.toComponent("&6⚠️ [警告] 检测到默认SMTP服务器地址未修改，邮件发送功能可能无法正常工作！"));
                hasDefaultConfig = true;
            }
        }
        
        // 检查QQ API配置
        boolean qqApiEnabled = getConfig().getBoolean("qqapi.enable", false);
        String qqAppId = getConfig().getString("qqapi.appId", "your_app_id");
        String qqAppKey = getConfig().getString("qqapi.appKey", "your_app_key");
        String qqRedirectUri = getConfig().getString("qqapi.redirectUri", "https://your_domain.com/callback");
        
        if (qqApiEnabled) {
            if (qqAppId.equals("your_app_id")) {
                getServer().sendMessage(ColorUtils.toComponent("&6⚠️ [警告] 检测到默认QQ API AppID未修改，QQ登录功能可能无法正常工作！"));
                hasDefaultConfig = true;
            }
            
            if (qqAppKey.equals("your_app_key")) {
                getServer().sendMessage(ColorUtils.toComponent("&6⚠️ [警告] 检测到默认QQ API AppKey未修改，QQ登录功能可能无法正常工作！"));
                hasDefaultConfig = true;
            }
            
            if (qqRedirectUri.equals("https://your_domain.com/callback")) {
                getServer().sendMessage(ColorUtils.toComponent("&6⚠️ [警告] 检测到默认QQ API回调地址未修改，QQ登录功能可能无法正常工作！"));
                hasDefaultConfig = true;
            }
        }
        
        // 检查数据库配置（仅当使用SQL存储时）
        String storageType = getConfig().getString("database.storage", "json");
        if (storageType.equals("sql")) {
            String dbUsername = getConfig().getString("database.sql.username", "root");
            String dbPassword = getConfig().getString("database.sql.password", "password");
            String dbName = getConfig().getString("database.sql.database", "announcement_compensation");
            
            if (dbUsername.equals("root")) {
                getServer().sendMessage(ColorUtils.toComponent("&6⚠️ [警告] 检测到默认数据库用户名 'root' 未修改，存在安全风险，请及时更新配置！"));
                hasDefaultConfig = true;
            }
            
            if (dbPassword.equals("password")) {
                getServer().sendMessage(ColorUtils.toComponent("&6⚠️ [警告] 检测到默认数据库密码 'password' 未修改，存在安全风险，请及时更新配置！"));
                hasDefaultConfig = true;
            }
            
            if (dbName.equals("announcement_compensation")) {
                getServer().sendMessage(ColorUtils.toComponent("&6⚠️ [警告] 检测到默认数据库名称未修改，请根据实际情况修改！"));
                hasDefaultConfig = true;
            }
        }
        
        if (!hasDefaultConfig) {
            getServer().sendMessage(ColorUtils.toComponent("&a✅ [安全检查] 未发现默认配置，所有关键配置项已修改！"));
        } else {
            getServer().sendMessage(ColorUtils.toComponent("&c❌ [安全检查] 发现默认配置项，请及时修改以确保服务器安全！"));
        }
    }
}