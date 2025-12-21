package com.server.database;

import com.server.AnnouncementCompensationPlugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 数据库连接管理器
 * 支持MySQL、PostgreSQL和H2数据库
 */
public class DatabaseManager {
    private final AnnouncementCompensationPlugin plugin;
    private HikariDataSource dataSource;
    private boolean useDatabase;
    private String storageType;

    public DatabaseManager(AnnouncementCompensationPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 初始化数据库连接
     */
    public void initialize() {
        try {
            storageType = plugin.getConfig().getString("database.storage", "json");
            
            if ("sql".equals(storageType)) {
                initializeSQLDatabase();
                useDatabase = true;
                plugin.getLogger().info("✅ 数据库连接初始化成功，使用SQL数据库");
            } else {
                useDatabase = false;
                plugin.getLogger().info("✅ 使用JSON文件存储数据");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("❌ 数据库连接初始化失败：" + e.getMessage());
            e.printStackTrace();
            
            // 如果数据库连接失败，回退到JSON存储
            useDatabase = false;
            plugin.getLogger().warning("⚠️ 回退到JSON文件存储模式");
        }
    }

    /**
     * 初始化SQL数据库连接
     */
    private void initializeSQLDatabase() throws SQLException {
        String dbType = plugin.getConfig().getString("database.sql.type", "mysql");
        String host = plugin.getConfig().getString("database.sql.host", "localhost");
        int port = plugin.getConfig().getInt("database.sql.port", 3306);
        String database = plugin.getConfig().getString("database.sql.database", "announcement_compensation");
        String username = plugin.getConfig().getString("database.sql.username", "root");
        String password = plugin.getConfig().getString("database.sql.password", "");
        
        // 连接池配置
        int minIdle = plugin.getConfig().getInt("database.sql.pool.min_idle", 5);
        int maxActive = plugin.getConfig().getInt("database.sql.pool.max_active", 50);
        int maxWait = plugin.getConfig().getInt("database.sql.pool.max_wait", 60000);

        HikariConfig config = new HikariConfig();
        
        // 设置连接池参数
        config.setMinimumIdle(minIdle);
        config.setMaximumPoolSize(maxActive);
        config.setIdleTimeout(maxWait);
        config.setConnectionTimeout(maxWait);
        config.setLeakDetectionThreshold(60000);
        config.setValidationTimeout(5000);

        // 根据数据库类型设置连接URL
        String jdbcUrl = getJdbcUrl(dbType, host, port, database);
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);

        // 数据库特定配置
        switch (dbType.toLowerCase()) {
            case "mysql":
                config.setDriverClassName("com.mysql.cj.jdbc.Driver");
                config.addDataSourceProperty("useSSL", "false");
                config.addDataSourceProperty("allowPublicKeyRetrieval", "true");
                config.addDataSourceProperty("serverTimezone", "UTC");
                config.addDataSourceProperty("useUnicode", "true");
                config.addDataSourceProperty("characterEncoding", "UTF-8");
                config.addDataSourceProperty("cachePrepStmts", "true");
                config.addDataSourceProperty("prepStmtCacheSize", "250");
                config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                config.addDataSourceProperty("useServerPrepStmts", "true");
                break;
                
            case "postgresql":
                config.setDriverClassName("org.postgresql.Driver");
                config.addDataSourceProperty("sslmode", "disable");
                config.addDataSourceProperty("stringtype", "unspecified");
                break;
                
            case "h2":
                config.setDriverClassName("org.h2.Driver");
                config.addDataSourceProperty("MODE", "MySQL");
                config.addDataSourceProperty("DB_CLOSE_DELAY", "-1");
                break;
                
            default:
                throw new SQLException("不支持的数据库类型：" + dbType);
        }

        // 创建数据源
        dataSource = new HikariDataSource(config);
        
        // 测试连接
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(5000)) {
                plugin.getLogger().info("✅ 数据库连接测试成功");
            }
        }
        
        // 执行数据库初始化脚本
        executeInitScript();
    }

    /**
     * 根据数据库类型获取JDBC URL
     */
    private String getJdbcUrl(String dbType, String host, int port, String database) {
        switch (dbType.toLowerCase()) {
            case "mysql":
                return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&useUnicode=true&characterEncoding=UTF-8", 
                    host, port, database);
            case "postgresql":
                return String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
            case "h2":
                return String.format("jdbc:h2:file:%s/data/%s;AUTO_SERVER=TRUE;MODE=MySQL;DB_CLOSE_DELAY=-1", 
                    plugin.getDataFolder().getAbsolutePath(), database);
            default:
                throw new IllegalArgumentException("不支持的数据库类型：" + dbType);
        }
    }

    /**
     * 获取数据库连接
     */
    public Connection getConnection() throws SQLException {
        if (dataSource != null && !dataSource.isClosed()) {
            return dataSource.getConnection();
        }
        throw new SQLException("数据库连接池未初始化或已关闭");
    }

    /**
     * 检查是否使用数据库存储
     */
    public boolean isUsingDatabase() {
        return useDatabase;
    }

    /**
     * 获取存储类型
     */
    public String getStorageType() {
        return storageType;
    }

    /**
     * 获取数据源（用于管理）
     */
    public HikariDataSource getDataSource() {
        return dataSource;
    }

    /**
     * 关闭数据库连接池
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("✅ 数据库连接池已关闭");
        }
    }

    /**
     * 执行SQL脚本初始化表结构
     */
    public void executeInitScript() throws SQLException {
        if (!useDatabase) {
            return;
        }

        String dbType = plugin.getConfig().getString("database.sql.type", "mysql");
        
        // 创建表结构的SQL脚本
        String[] createTables = getCreateTableSQL(dbType);
        
        try (Connection conn = getConnection()) {
            for (String sql : createTables) {
                if (!sql.trim().isEmpty() && !sql.trim().startsWith("--")) {
                    try {
                        conn.createStatement().execute(sql);
                        plugin.getLogger().info("✅ 执行SQL脚本成功");
                    } catch (SQLException e) {
                        plugin.getLogger().warning("⚠️ 执行SQL脚本时出现警告：" + e.getMessage());
                        // 忽略表已存在的错误
                        if (!e.getMessage().toLowerCase().contains("already exists")) {
                            throw e;
                        }
                    }
                }
            }
        }
    }

    /**
     * 获取创建表的SQL脚本
     */
    private String[] getCreateTableSQL(String dbType) {
        switch (dbType.toLowerCase()) {
            case "mysql":
                return new String[]{
                    // 管理员表
                    "CREATE TABLE IF NOT EXISTS admins (" +
                    "    id INT AUTO_INCREMENT PRIMARY KEY," +
                    "    username VARCHAR(50) UNIQUE NOT NULL," +
                    "    password_hash VARCHAR(255) NOT NULL," +
                    "    permissions JSON," +
                    "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                    ");",
                    
                    // 公告表
                    "CREATE TABLE IF NOT EXISTS announcements (" +
                    "    id INT AUTO_INCREMENT PRIMARY KEY," +
                    "    title VARCHAR(255) NOT NULL," +
                    "    content TEXT NOT NULL," +
                    "    author VARCHAR(50) NOT NULL," +
                    "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                    ");",
                    
                    // 补偿表
                    "CREATE TABLE IF NOT EXISTS compensations (" +
                    "    id INT AUTO_INCREMENT PRIMARY KEY," +
                    "    title VARCHAR(255) NOT NULL," +
                    "    description TEXT," +
                    "    items JSON," +
                    "    author VARCHAR(50) NOT NULL," +
                    "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                    ");",
                    
                    // 领取日志表
                    "CREATE TABLE IF NOT EXISTS claim_logs (" +
                    "    id INT AUTO_INCREMENT PRIMARY KEY," +
                    "    compensation_id INT," +
                    "    player_name VARCHAR(50) NOT NULL," +
                    "    player_uuid VARCHAR(36)," +
                    "    claim_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "    FOREIGN KEY (compensation_id) REFERENCES compensations(id)" +
                    ");",
                    
                    // 白名单表
                    "CREATE TABLE IF NOT EXISTS whitelist_entries (" +
                    "    id INT AUTO_INCREMENT PRIMARY KEY," +
                    "    player_name VARCHAR(50) UNIQUE NOT NULL," +
                    "    player_uuid VARCHAR(36)," +
                    "    added_by VARCHAR(50) NOT NULL," +
                    "    reason TEXT," +
                    "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");",
                    
                    // 用户表
                    "CREATE TABLE IF NOT EXISTS users (" +
                    "    id INT AUTO_INCREMENT PRIMARY KEY," +
                    "    username VARCHAR(50) UNIQUE NOT NULL," +
                    "    email VARCHAR(255) UNIQUE," +
                    "    password_hash VARCHAR(255)," +
                    "    qq_number VARCHAR(20)," +
                    "    minecraft_uuid VARCHAR(36)," +
                    "    is_verified BOOLEAN DEFAULT FALSE," +
                    "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                    ");",
                    
                    // 邮箱验证表
                    "CREATE TABLE IF NOT EXISTS email_verification_codes (" +
                    "    id INT AUTO_INCREMENT PRIMARY KEY," +
                    "    email VARCHAR(255) NOT NULL," +
                    "    code VARCHAR(10) NOT NULL," +
                    "    expires_at TIMESTAMP NOT NULL," +
                    "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");",
                    
                    // 白名单启用状态表
                    "CREATE TABLE IF NOT EXISTS settings (" +
                    "    id INT AUTO_INCREMENT PRIMARY KEY," +
                    "    setting_key VARCHAR(50) UNIQUE NOT NULL," +
                    "    setting_value TEXT NOT NULL," +
                    "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                    ");"
                };
                
            case "postgresql":
                return new String[]{
                    // PostgreSQL版本的SQL脚本（类似MySQL但语法略有不同）
                    "CREATE TABLE IF NOT EXISTS admins (" +
                    "    id SERIAL PRIMARY KEY," +
                    "    username VARCHAR(50) UNIQUE NOT NULL," +
                    "    password_hash VARCHAR(255) NOT NULL," +
                    "    permissions JSONB," +
                    "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");",
                    
                    "CREATE TABLE IF NOT EXISTS announcements (" +
                    "    id SERIAL PRIMARY KEY," +
                    "    title VARCHAR(255) NOT NULL," +
                    "    content TEXT NOT NULL," +
                    "    author VARCHAR(50) NOT NULL," +
                    "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");",
                    
                    "CREATE TABLE IF NOT EXISTS compensations (" +
                    "    id SERIAL PRIMARY KEY," +
                    "    title VARCHAR(255) NOT NULL," +
                    "    description TEXT," +
                    "    items JSONB," +
                    "    author VARCHAR(50) NOT NULL," +
                    "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");",
                    
                    "CREATE TABLE IF NOT EXISTS claim_logs (" +
                    "    id SERIAL PRIMARY KEY," +
                    "    compensation_id INTEGER," +
                    "    player_name VARCHAR(50) NOT NULL," +
                    "    player_uuid VARCHAR(36)," +
                    "    claim_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");",
                    
                    "CREATE TABLE IF NOT EXISTS whitelist_entries (" +
                    "    id SERIAL PRIMARY KEY," +
                    "    player_name VARCHAR(50) UNIQUE NOT NULL," +
                    "    player_uuid VARCHAR(36)," +
                    "    added_by VARCHAR(50) NOT NULL," +
                    "    reason TEXT," +
                    "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");",
                    
                    "CREATE TABLE IF NOT EXISTS users (" +
                    "    id SERIAL PRIMARY KEY," +
                    "    username VARCHAR(50) UNIQUE NOT NULL," +
                    "    email VARCHAR(255) UNIQUE," +
                    "    password_hash VARCHAR(255)," +
                    "    qq_number VARCHAR(20)," +
                    "    minecraft_uuid VARCHAR(36)," +
                    "    is_verified BOOLEAN DEFAULT FALSE," +
                    "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");",
                    
                    "CREATE TABLE IF NOT EXISTS email_verification_codes (" +
                    "    id SERIAL PRIMARY KEY," +
                    "    email VARCHAR(255) NOT NULL," +
                    "    code VARCHAR(10) NOT NULL," +
                    "    expires_at TIMESTAMP NOT NULL," +
                    "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");",
                    
                    "CREATE TABLE IF NOT EXISTS settings (" +
                    "    id SERIAL PRIMARY KEY," +
                    "    setting_key VARCHAR(50) UNIQUE NOT NULL," +
                    "    setting_value TEXT NOT NULL," +
                    "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");"
                };
                
            case "h2":
                return new String[]{
                    // H2版本的SQL脚本
                    "CREATE TABLE IF NOT EXISTS admins (" +
                    "    id INT AUTO_INCREMENT PRIMARY KEY," +
                    "    username VARCHAR(50) UNIQUE NOT NULL," +
                    "    password_hash VARCHAR(255) NOT NULL," +
                    "    permissions VARCHAR(1000)," +
                    "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");",
                    
                    "CREATE TABLE IF NOT EXISTS announcements (" +
                    "    id INT AUTO_INCREMENT PRIMARY KEY," +
                    "    title VARCHAR(255) NOT NULL," +
                    "    content CLOB NOT NULL," +
                    "    author VARCHAR(50) NOT NULL," +
                    "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");",
                    
                    "CREATE TABLE IF NOT EXISTS compensations (" +
                    "    id INT AUTO_INCREMENT PRIMARY KEY," +
                    "    title VARCHAR(255) NOT NULL," +
                    "    description CLOB," +
                    "    items VARCHAR(2000)," +
                    "    author VARCHAR(50) NOT NULL," +
                    "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");",
                    
                    "CREATE TABLE IF NOT EXISTS claim_logs (" +
                    "    id INT AUTO_INCREMENT PRIMARY KEY," +
                    "    compensation_id INT," +
                    "    player_name VARCHAR(50) NOT NULL," +
                    "    player_uuid VARCHAR(36)," +
                    "    claim_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");",
                    
                    "CREATE TABLE IF NOT EXISTS whitelist_entries (" +
                    "    id INT AUTO_INCREMENT PRIMARY KEY," +
                    "    player_name VARCHAR(50) UNIQUE NOT NULL," +
                    "    player_uuid VARCHAR(36)," +
                    "    added_by VARCHAR(50) NOT NULL," +
                    "    reason CLOB," +
                    "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");",
                    
                    "CREATE TABLE IF NOT EXISTS users (" +
                    "    id INT AUTO_INCREMENT PRIMARY KEY," +
                    "    username VARCHAR(50) UNIQUE NOT NULL," +
                    "    email VARCHAR(255) UNIQUE," +
                    "    password_hash VARCHAR(255)," +
                    "    qq_number VARCHAR(20)," +
                    "    minecraft_uuid VARCHAR(36)," +
                    "    is_verified BOOLEAN DEFAULT FALSE," +
                    "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");",
                    
                    "CREATE TABLE IF NOT EXISTS email_verification_codes (" +
                    "    id INT AUTO_INCREMENT PRIMARY KEY," +
                    "    email VARCHAR(255) NOT NULL," +
                    "    code VARCHAR(10) NOT NULL," +
                    "    expires_at TIMESTAMP NOT NULL," +
                    "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");",
                    
                    "CREATE TABLE IF NOT EXISTS settings (" +
                    "    id INT AUTO_INCREMENT PRIMARY KEY," +
                    "    setting_key VARCHAR(50) UNIQUE NOT NULL," +
                    "    setting_value CLOB NOT NULL," +
                    "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");"
                };
                
            default:
                return new String[0];
        }
    }
}