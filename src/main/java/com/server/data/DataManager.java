package com.server.data;

import com.server.AnnouncementCompensationPlugin;
import com.server.database.DatabaseManager;
import com.server.database.DatabaseDataManager;
import com.server.data.model.Admin;
import com.server.data.model.Announcement;
import com.server.data.model.Compensation;
import com.server.data.model.ClaimLog;
import com.server.data.model.WhitelistEntry;
import com.server.data.model.User;

import com.server.data.model.EmailVerificationCode;
import com.server.util.GsonUtils;
import com.server.util.TimeUtils;
import com.server.util.BCryptUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据管理器核心类
 * 支持JSON文件存储和SQL数据库存储两种模式
 * 负责所有数据的读写、持久化，适配JDK 17的NIO文件操作
 * 核心职责：管理员/公告/补偿/白名单/日志数据的增删改查 + 白名单启用状态管理
 */
public class DataManager {
    // 插件实例（获取数据目录路径）
    private final AnnouncementCompensationPlugin plugin;
    // 数据目录路径（plugins/AnnouncementCompensation/）
    private final File dataFolder;
    
    // 数据库管理器
    private final DatabaseManager databaseManager;
    private final DatabaseDataManager databaseDataManager;
    
    // 内存数据缓存（减少文件IO次数）
    private List<Admin> admins = new ArrayList<>();
    private List<Announcement> announcements = new ArrayList<>();
    private List<Compensation> compensations = new ArrayList<>();
    private List<WhitelistEntry> whitelistEntries = new ArrayList<>();
    private List<ClaimLog> claimLogs = new ArrayList<>();
    private List<User> users = new ArrayList<>();
    private List<EmailVerificationCode> emailVerificationCodes = new ArrayList<>();
    // 白名单启用状态（默认禁用）
    private boolean whitelistEnabled = false;
    
    // 存储类型：json 或 sql
    private String storageType;

    /**
     * 构造方法：关联插件实例，初始化数据目录和数据库管理器
     */
    public DataManager(AnnouncementCompensationPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.dataFolder = plugin.getDataFolder();
        this.databaseManager = databaseManager;
        this.databaseDataManager = new DatabaseDataManager(plugin, databaseManager);
        this.storageType = plugin.getConfig().getString("database.storage", "json");
    }

    // ====================== 数据加载/保存核心方法 ======================
    /**
     * 加载所有数据（插件启动时调用）
     */
    public void loadAllData() {
        try {
            // 确保数据目录存在
            if (!dataFolder.exists()) dataFolder.mkdirs();

            // 根据存储类型选择加载方式
            if ("sql".equals(storageType) && databaseManager.isUsingDatabase()) {
                // 使用数据库加载
                plugin.getLogger().info("🔄 从SQL数据库加载数据...");
                admins = databaseDataManager.loadAdmins();
                announcements = databaseDataManager.loadAnnouncements();
                compensations = databaseDataManager.loadCompensations();
                whitelistEntries = databaseDataManager.loadWhitelistEntries();
                claimLogs = databaseDataManager.loadClaimLogs();
                users = databaseDataManager.loadUsers();
                emailVerificationCodes = databaseDataManager.loadEmailVerificationCodes();
                whitelistEnabled = databaseDataManager.loadWhitelistEnabledStatus();
            } else {
                // 使用JSON文件加载
                plugin.getLogger().info("🔄 从JSON文件加载数据...");
                loadWhitelistEnabledStatus();
                loadAdmins();
                loadAnnouncements();
                loadCompensations();
                loadWhitelistEntries();
                loadClaimLogs();
                loadUsers();
                loadEmailVerificationCodes();
            }
            
            plugin.getLogger().info("✅ 所有数据加载完成：");
            plugin.getLogger().info("  - 存储方式：" + (databaseManager.isUsingDatabase() ? "SQL数据库" : "JSON文件"));
            plugin.getLogger().info("  - 管理员数量：" + admins.size());
            plugin.getLogger().info("  - 公告数量：" + announcements.size());
            plugin.getLogger().info("  - 补偿数量：" + compensations.size());
            plugin.getLogger().info("  - 白名单数量：" + whitelistEntries.size());
            plugin.getLogger().info("  - 领取日志数量：" + claimLogs.size());
            plugin.getLogger().info("  - 用户数量：" + users.size());
        } catch (Exception e) {
            plugin.getLogger().severe("❌ 数据加载失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 保存所有数据（插件禁用时调用）
     */
    public void saveAllData() {
        try {
            // 根据存储类型选择保存方式
            if ("sql".equals(storageType) && databaseManager.isUsingDatabase()) {
                // 使用数据库保存
                plugin.getLogger().info("🔄 保存数据到SQL数据库...");
                databaseDataManager.saveAdmins(admins);
                databaseDataManager.saveAnnouncements(announcements);
                databaseDataManager.saveCompensations(compensations);
                databaseDataManager.saveWhitelistEntries(whitelistEntries);
                databaseDataManager.saveClaimLogs(claimLogs);
                databaseDataManager.saveUsers(users);
                databaseDataManager.saveEmailVerificationCodes(emailVerificationCodes);
                databaseDataManager.saveWhitelistEnabledStatus(whitelistEnabled);
            } else {
                // 使用JSON文件保存
                plugin.getLogger().info("🔄 保存数据到JSON文件...");
                saveWhitelistEnabledStatus();
                saveAdmins();
                saveAnnouncements();
                saveCompensations();
                saveWhitelistEntries();
                saveClaimLogs();
                saveUsers();
                saveEmailVerificationCodes();
            }
            
            plugin.getLogger().info("✅ 所有数据保存完成");
        } catch (Exception e) {
            plugin.getLogger().severe("❌ 数据保存失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    // ====================== 新增：确保至少存在一个管理员（来自config或默认） ======================
    /**
     * 如果当前没有管理员，尝试从config读取web.login配置创建默认管理员；
     * 若config中password不是BCrypt哈希，则使用BCrypt加密后保存。
     * 默认权限为 ac.web.*
     */
    public void ensureDefaultAdminFromConfig() {
        try {
            String cfgUsername = plugin.getConfig().getString("web.login.username", "admin");
            String cfgPassword = plugin.getConfig().getString("web.login.password", null);
            boolean override = plugin.getConfig().getBoolean("web.login.override", false);
            String passwordHash;

            // 无管理员时按原逻辑创建默认管理员并持久化
            if (admins.isEmpty()) {
                if (cfgPassword == null || cfgPassword.isEmpty()) {
                    passwordHash = BCryptUtils.encrypt("admin123");
                } else {
                    if (cfgPassword.startsWith("$2a$") || cfgPassword.startsWith("$2y$") || cfgPassword.startsWith("$2b$")) {
                        passwordHash = cfgPassword;
                    } else {
                        passwordHash = BCryptUtils.encrypt(cfgPassword);
                    }
                }

                Admin defaultAdmin = new Admin();
                defaultAdmin.setUsername(cfgUsername);
                defaultAdmin.setPasswordHash(passwordHash);
                defaultAdmin.setPermissions(List.of("ac.web.*"));

                admins.add(defaultAdmin);
                saveAdmins();
                plugin.getLogger().info("✅ 已创建默认管理员：" + cfgUsername);
                return;
            }

            // 如果已有管理员，尝试查找同名管理员
            Admin existing = getAdminByUsername(cfgUsername);
            if (existing == null) {
                // 若开启 override，可以使用 config 创建新管理员
                if (override) {
                    if (cfgPassword == null || cfgPassword.isEmpty()) {
                        passwordHash = BCryptUtils.encrypt("admin123");
                    } else {
                        if (cfgPassword.startsWith("$2a$") || cfgPassword.startsWith("$2y$") || cfgPassword.startsWith("$2b$")) {
                            passwordHash = cfgPassword;
                        } else {
                            passwordHash = BCryptUtils.encrypt(cfgPassword);
                        }
                    }

                    Admin newAdmin = new Admin();
                    newAdmin.setUsername(cfgUsername);
                    newAdmin.setPasswordHash(passwordHash);
                    newAdmin.setPermissions(List.of("ac.web.*"));

                    admins.add(newAdmin);
                    saveAdmins();
                    plugin.getLogger().info("✅ 根据 config.yml 创建了管理员：" + cfgUsername);
                } else {
                    plugin.getLogger().info("ℹ️ 已存在管理员，未启用 web.login.override，跳过从 config 创建管理员。");
                }
                return;
            }

            // 找到同名管理员，只有在 override 为 true 时才覆盖密码
            if (!override) {
                plugin.getLogger().info("ℹ️ 管理员已存在且未启用 web.login.override，跳过覆盖。");
                return;
            }

            // override = true：尝试更新密码（若 config 提供）
            if (cfgPassword == null || cfgPassword.isEmpty()) {
                plugin.getLogger().warning("⚠️ web.login.override = true，但未配置 web.login.password，未修改密码。");
                return;
            }

            if (cfgPassword.startsWith("$2a$") || cfgPassword.startsWith("$2y$") || cfgPassword.startsWith("$2b$")) {
                passwordHash = cfgPassword;
            } else {
                passwordHash = BCryptUtils.encrypt(cfgPassword);
            }

            existing.setPasswordHash(passwordHash);
            saveAdmins();
            plugin.getLogger().info("✅ 已使用 config.yml 的密码更新管理员：" + cfgUsername);
        } catch (Exception e) {
            plugin.getLogger().severe("❌ 创建/更新默认管理员失败：" + e.getMessage());
        }
    }

    // ====================== 管理员数据操作 ======================
    /**
     * 加载管理员数据（从 admins/ 目录下的JSON文件）
     */
    private void loadAdmins() throws IOException {
        File adminsDir = new File(dataFolder, "admins");
        if (!adminsDir.exists()) return;

        // JDK 17 Stream流遍历文件，适配NIO
        Files.list(Paths.get(adminsDir.getPath()))
             .filter(path -> path.toString().endsWith(".json"))
             .forEach(path -> {
                 try (FileInputStream fis = new FileInputStream(path.toFile())) {
                     String json = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
                     Admin admin = GsonUtils.getGson().fromJson(json, Admin.class);
                     if (admin != null && admin.getUsername() != null) {
                         admins.add(admin);
                     }
                 } catch (Exception e) {
                     plugin.getLogger().warning("⚠️ 加载管理员文件失败：" + path.getFileName() + " → " + e.getMessage());
                 }
             });
    }

    /**
     * 保存所有管理员数据
     */
    private void saveAdmins() throws IOException {
        File adminsDir = new File(dataFolder, "admins");
        if (!adminsDir.exists()) adminsDir.mkdirs();

        // 清空旧文件（避免冗余）
        Files.list(Paths.get(adminsDir.getPath()))
             .filter(path -> path.toString().endsWith(".json"))
             .forEach(path -> {
                 try {
                     Files.delete(path);
                 } catch (IOException e) {
                     plugin.getLogger().warning("⚠️ 删除旧管理员文件失败：" + path.getFileName());
                 }
             });

        // 保存新数据（按用户名命名文件）
        for (Admin admin : admins) {
            File adminFile = new File(adminsDir, admin.getUsername() + ".json");
            try (FileOutputStream fos = new FileOutputStream(adminFile)) {
                String json = GsonUtils.getGson().toJson(admin);
                fos.write(json.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    /**
     * 根据用户名获取管理员信息
     */
    public Admin getAdminByUsername(String username) {
        return admins.stream()
                     .filter(admin -> admin.getUsername().equals(username))
                     .findFirst()
                     .orElse(null);
    }

    // ====================== 公告数据操作 ======================
    /**
     * 加载公告数据
     */
    private void loadAnnouncements() throws IOException {
        File announcementsDir = new File(dataFolder, "announcements");
        if (!announcementsDir.exists()) return;

        Files.list(Paths.get(announcementsDir.getPath()))
             .filter(path -> path.toString().endsWith(".json"))
             .forEach(path -> {
                 try (FileInputStream fis = new FileInputStream(path.toFile())) {
                     String json = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
                     Announcement announcement = GsonUtils.getGson().fromJson(json, Announcement.class);
                     if (announcement != null) {
                         announcements.add(announcement);
                     }
                 } catch (Exception e) {
                     plugin.getLogger().warning("⚠️ 加载公告文件失败：" + path.getFileName() + " → " + e.getMessage());
                 }
             });
    }

    /**
     * 保存所有公告数据
     */
    private void saveAnnouncements() throws IOException {
        File announcementsDir = new File(dataFolder, "announcements");
        if (!announcementsDir.exists()) announcementsDir.mkdirs();

        // 清空旧文件
        Files.list(Paths.get(announcementsDir.getPath()))
             .filter(path -> path.toString().endsWith(".json"))
             .forEach(path -> {
                 try {
                     Files.delete(path);
                 } catch (IOException e) {
                     plugin.getLogger().warning("⚠️ 删除旧公告文件失败：" + path.getFileName());
                 }
             });

        // 保存新数据（按UUID命名文件）
        for (Announcement announcement : announcements) {
            File annFile = new File(announcementsDir, announcement.getIdString() + ".json");
            try (FileOutputStream fos = new FileOutputStream(annFile)) {
                String json = GsonUtils.getGson().toJson(announcement);
                fos.write(json.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    /**
     * 获取所有公告
     */
    public List<Announcement> getAllAnnouncements() {
        return Collections.unmodifiableList(announcements);
    }

    /**
     * 保存单个公告（新增/修改）
     */
    public void saveAnnouncement(Announcement announcement) {
        // 自动生成ID（新增时）
        if (announcement.getId() <= 0) {
            // 这里应该从数据库自动递增获取ID，暂时设为1表示新增
            announcement.setId(1);
        }
        // 自动填充创建时间
        if (announcement.getCreateTime() == null) {
            announcement.setCreateTime(TimeUtils.getCurrentTimeStr());
        }

        // 更新缓存：先移除旧的，再添加新的
        announcements.removeIf(a -> a.getId() == announcement.getId());
        announcements.add(announcement);

        // 立即保存到文件（避免内存数据丢失）
        try {
            File announcementsDir = new File(dataFolder, "announcements");
            if (!announcementsDir.exists()) announcementsDir.mkdirs();
            
            File annFile = new File(announcementsDir, announcement.getIdString() + ".json");
            try (FileOutputStream fos = new FileOutputStream(annFile)) {
                String json = GsonUtils.getGson().toJson(announcement);
                fos.write(json.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            plugin.getLogger().severe("❌ 保存公告失败：" + e.getMessage());
        }
    }

    /**
     * 删除公告
     */
    public boolean deleteAnnouncement(String id) {
        boolean removed = announcements.removeIf(a -> a.getIdString().equals(id));
        if (removed) {
            // 删除文件
            File annFile = new File(dataFolder, "announcements/" + id + ".json");
            if (annFile.exists()) {
                annFile.delete();
            }
        }
        return removed;
    }

    /**
     * 获取待发送的定时公告
     */
    public List<Announcement> getTimedAnnouncements() {
        return announcements.stream()
                            .filter(a -> !a.isSent() && a.getSendTime() != null && !a.getSendTime().isEmpty())
                            .collect(Collectors.toList());
    }

    // ====================== 补偿数据操作 ======================
    /**
     * 加载补偿数据
     */
    private void loadCompensations() throws IOException {
        File compensationsDir = new File(dataFolder, "compensations");
        if (!compensationsDir.exists()) return;

        Files.list(Paths.get(compensationsDir.getPath()))
             .filter(path -> path.toString().endsWith(".json"))
             .forEach(path -> {
                 try (FileInputStream fis = new FileInputStream(path.toFile())) {
                     String json = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
                     Compensation compensation = GsonUtils.getGson().fromJson(json, Compensation.class);
                     if (compensation != null && compensation.getId() > 0) {
                         compensations.add(compensation);
                     }
                 } catch (Exception e) {
                     plugin.getLogger().warning("⚠️ 加载补偿文件失败：" + path.getFileName() + " → " + e.getMessage());
                 }
             });
    }

    /**
     * 保存所有补偿数据
     */
    private void saveCompensations() throws IOException {
        File compensationsDir = new File(dataFolder, "compensations");
        if (!compensationsDir.exists()) compensationsDir.mkdirs();

        // 清空旧文件
        Files.list(Paths.get(compensationsDir.getPath()))
             .filter(path -> path.toString().endsWith(".json"))
             .forEach(path -> {
                 try {
                     Files.delete(path);
                 } catch (IOException e) {
                     plugin.getLogger().warning("⚠️ 删除旧补偿文件失败：" + path.getFileName());
                 }
             });

        // 保存新数据
        for (Compensation compensation : compensations) {
            File compFile = new File(compensationsDir, compensation.getIdString() + ".json");
            try (FileOutputStream fos = new FileOutputStream(compFile)) {
                String json = GsonUtils.getGson().toJson(compensation);
                fos.write(json.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    /**
     * 获取所有补偿
     */
    public List<Compensation> getAllCompensations() {
        return Collections.unmodifiableList(compensations);
    }

    /**
     * 保存单个补偿
     */
    public void saveCompensation(Compensation compensation) {
        if (compensation.getId() <= 0) {
            compensation.setId(1);
        }
        if (compensation.getCreateTime() == null) {
            compensation.setCreateTime(TimeUtils.getCurrentTimeStr());
        }

        compensations.removeIf(c -> c.getId() == compensation.getId());
        compensations.add(compensation);

        try {
            File compensationsDir = new File(dataFolder, "compensations");
            if (!compensationsDir.exists()) compensationsDir.mkdirs();
            
            File compFile = new File(compensationsDir, compensation.getIdString() + ".json");
            try (FileOutputStream fos = new FileOutputStream(compFile)) {
                String json = GsonUtils.getGson().toJson(compensation);
                fos.write(json.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            plugin.getLogger().severe("❌ 保存补偿失败：" + e.getMessage());
        }
    }

    /**
     * 删除补偿
     */
    public boolean deleteCompensation(String id) {
        boolean removed = compensations.removeIf(c -> c.getIdString().equals(id));
        if (removed) {
            File compFile = new File(dataFolder, "compensations/" + id + ".json");
            if (compFile.exists()) {
                compFile.delete();
            }
        }
        return removed;
    }

    /**
     * 根据ID获取补偿
     */
    public Compensation getCompensationById(String id) {
        return compensations.stream()
                            .filter(c -> c.getIdString().equals(id))
                            .findFirst()
                            .orElse(null);
    }

    // ====================== 白名单数据操作 ======================
    /**
     * 加载白名单数据
     */
    private void loadWhitelistEntries() throws IOException {
        File whitelistDir = new File(dataFolder, "whitelist");
        if (!whitelistDir.exists()) return;

        Files.list(Paths.get(whitelistDir.getPath()))
             .filter(path -> path.toString().endsWith(".json"))
             .forEach(path -> {
                 try (FileInputStream fis = new FileInputStream(path.toFile())) {
                     String json = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
                     WhitelistEntry entry = GsonUtils.getGson().fromJson(json, WhitelistEntry.class);
                     if (entry != null && entry.getUuid() != null) {
                         whitelistEntries.add(entry);
                     }
                 } catch (Exception e) {
                     plugin.getLogger().warning("⚠️ 加载白名单文件失败：" + path.getFileName() + " → " + e.getMessage());
                 }
             });
    }

    /**
     * 保存所有白名单数据
     */
    private void saveWhitelistEntries() throws IOException {
        File whitelistDir = new File(dataFolder, "whitelist");
        if (!whitelistDir.exists()) whitelistDir.mkdirs();

        // 清空旧文件
        Files.list(Paths.get(whitelistDir.getPath()))
             .filter(path -> path.toString().endsWith(".json"))
             .forEach(path -> {
                 try {
                     Files.delete(path);
                 } catch (IOException e) {
                     plugin.getLogger().warning("⚠️ 删除旧白名单文件失败：" + path.getFileName());
                 }
             });

        // 保存新数据（按UUID命名）
        for (WhitelistEntry entry : whitelistEntries) {
            File entryFile = new File(whitelistDir, entry.getUuid() + ".json");
            try (FileOutputStream fos = new FileOutputStream(entryFile)) {
                String json = GsonUtils.getGson().toJson(entry);
                fos.write(json.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    /**
     * 获取所有白名单
     */
    public List<WhitelistEntry> getAllWhitelistEntries() {
        return Collections.unmodifiableList(whitelistEntries);
    }

    /**
     * 添加白名单
     */
    public void addWhitelistEntry(WhitelistEntry entry) {
        // 自动填充添加时间
        if (entry.getAddTime() == null) {
            entry.setAddTime(TimeUtils.getCurrentTimeStr());
        }

        // 先移除旧的（避免重复）
        whitelistEntries.removeIf(e -> e.getUuid().equals(entry.getUuid()));
        whitelistEntries.add(entry);

        // 立即保存
        try {
            File whitelistDir = new File(dataFolder, "whitelist");
            if (!whitelistDir.exists()) whitelistDir.mkdirs();
            
            File entryFile = new File(whitelistDir, entry.getUuid() + ".json");
            try (FileOutputStream fos = new FileOutputStream(entryFile)) {
                String json = GsonUtils.getGson().toJson(entry);
                fos.write(json.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            plugin.getLogger().severe("❌ 添加白名单失败：" + e.getMessage());
        }
    }

    /**
     * 删除白名单
     */
    public boolean deleteWhitelistEntry(String uuid) {
        boolean removed = whitelistEntries.removeIf(e -> e.getUuid().equals(uuid));
        if (removed) {
            File entryFile = new File(dataFolder, "whitelist/" + uuid + ".json");
            if (entryFile.exists()) {
                entryFile.delete();
            }
        }
        return removed;
    }

    /**
     * 检查玩家是否在白名单中
     */
    public boolean isPlayerInWhitelist(String uuid) {
        return whitelistEntries.stream()
                               .anyMatch(e -> e.getUuid().equals(uuid));
    }

    /**
     * 加载白名单启用状态
     */
    private void loadWhitelistEnabledStatus() throws IOException {
        File statusFile = new File(dataFolder, "whitelist_enabled.json");
        if (!statusFile.exists()) return;

        try (FileInputStream fis = new FileInputStream(statusFile)) {
            String json = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
            // 简单JSON解析：{"enabled":true}
            whitelistEnabled = GsonUtils.getGson().fromJson(json, Boolean.class);
        }
    }

    /**
     * 保存白名单启用状态
     */
    private void saveWhitelistEnabledStatus() throws IOException {
        File statusFile = new File(dataFolder, "whitelist_enabled.json");
        try (FileOutputStream fos = new FileOutputStream(statusFile)) {
            String json = GsonUtils.getGson().toJson(whitelistEnabled);
            fos.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * 获取/设置白名单启用状态
     */
    public boolean isWhitelistEnabled() {
        return whitelistEnabled;
    }

    public void setWhitelistEnabled(boolean enabled) {
        this.whitelistEnabled = enabled;
        // 立即保存状态
        try {
            saveWhitelistEnabledStatus();
        } catch (Exception e) {
            plugin.getLogger().severe("❌ 保存白名单状态失败：" + e.getMessage());
        }
    }

    // ====================== 领取日志数据操作 ======================
    /**
     * 加载领取日志
     */
    private void loadClaimLogs() throws IOException {
        File logsDir = new File(dataFolder, "logs");
        if (!logsDir.exists()) return;

        Files.list(Paths.get(logsDir.getPath()))
             .filter(path -> path.toString().endsWith(".json"))
             .forEach(path -> {
                 try (FileInputStream fis = new FileInputStream(path.toFile())) {
                     String json = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
                     ClaimLog log = GsonUtils.getGson().fromJson(json, ClaimLog.class);
                     if (log != null && log.getId() > 0) {
                         claimLogs.add(log);
                     }
                 } catch (Exception e) {
                     plugin.getLogger().warning("⚠️ 加载日志文件失败：" + path.getFileName() + " → " + e.getMessage());
                 }
             });
    }

    /**
     * 保存所有领取日志
     */
    private void saveClaimLogs() throws IOException {
        File logsDir = new File(dataFolder, "logs");
        if (!logsDir.exists()) logsDir.mkdirs();

        // 清空旧文件
        Files.list(Paths.get(logsDir.getPath()))
             .filter(path -> path.toString().endsWith(".json"))
             .forEach(path -> {
                 try {
                     Files.delete(path);
                 } catch (IOException e) {
                     plugin.getLogger().warning("⚠️ 删除旧日志文件失败：" + path.getFileName());
                 }
             });

        // 保存新数据
        for (ClaimLog log : claimLogs) {
            File logFile = new File(logsDir, log.getIdString() + ".json");
            try (FileOutputStream fos = new FileOutputStream(logFile)) {
                String json = GsonUtils.getGson().toJson(log);
                fos.write(json.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    /**
     * 获取所有领取日志
     */
    public List<ClaimLog> getAllClaimLogs() {
        return Collections.unmodifiableList(claimLogs);
    }

    /**
     * 添加领取日志
     */
    public void addClaimLog(ClaimLog log) {
        if (log.getId() <= 0) {
            log.setId(1);
        }
        if (log.getClaimTime() == null) {
            log.setClaimTime(TimeUtils.getCurrentTimeStr());
        }

        claimLogs.add(log);

        // 立即保存
        try {
            File logsDir = new File(dataFolder, "logs");
            if (!logsDir.exists()) logsDir.mkdirs();
            
            File logFile = new File(logsDir, log.getIdString() + ".json");
            try (FileOutputStream fos = new FileOutputStream(logFile)) {
                String json = GsonUtils.getGson().toJson(log);
                fos.write(json.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            plugin.getLogger().severe("❌ 添加领取日志失败：" + e.getMessage());
        }
    }

    // ====================== 用户数据操作 ======================
    /**
     * 加载用户数据
     */
    private void loadUsers() throws IOException {
        File usersDir = new File(dataFolder, "users");
        if (!usersDir.exists()) return;

        Files.list(Paths.get(usersDir.getPath()))
             .filter(path -> path.toString().endsWith(".json"))
             .forEach(path -> {
                 try (FileInputStream fis = new FileInputStream(path.toFile())) {
                     String json = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
                     User user = GsonUtils.getGson().fromJson(json, User.class);
                     if (user != null && user.getUsername() != null) {
                         users.add(user);
                     }
                 } catch (Exception e) {
                     plugin.getLogger().warning("⚠️ 加载用户文件失败：" + path.getFileName() + " → " + e.getMessage());
                 }
             });
    }

    /**
     * 保存所有用户数据
     */
    private void saveUsers() throws IOException {
        File usersDir = new File(dataFolder, "users");
        if (!usersDir.exists()) usersDir.mkdirs();

        // 清空旧文件
        Files.list(Paths.get(usersDir.getPath()))
             .filter(path -> path.toString().endsWith(".json"))
             .forEach(path -> {
                 try {
                     Files.delete(path);
                 } catch (IOException e) {
                     plugin.getLogger().warning("⚠️ 删除旧用户文件失败：" + path.getFileName());
                 }
             });

        // 保存新数据（按用户名命名文件）
        for (User user : users) {
            File userFile = new File(usersDir, user.getUsername() + ".json");
            try (FileOutputStream fos = new FileOutputStream(userFile)) {
                String json = GsonUtils.getGson().toJson(user);
                fos.write(json.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    /**
     * 获取所有用户
     */
    public List<User> getAllUsers() {
        return Collections.unmodifiableList(users);
    }

    /**
     * 根据用户名获取用户
     */
    public User getUserByUsername(String username) {
        return users.stream()
                    .filter(user -> user.getUsername().equals(username))
                    .findFirst()
                    .orElse(null);
    }

    /**
     * 根据邮箱获取用户
     */
    public User getUserByEmail(String email) {
        return users.stream()
                    .filter(user -> user.getEmail().equals(email))
                    .findFirst()
                    .orElse(null);
    }

    /**
     * 根据游戏角色UUID获取用户
     */
    public User getUserByGameUUID(String gameUUID) {
        return users.stream()
                    .filter(user -> gameUUID.equals(user.getGameUUID()))
                    .findFirst()
                    .orElse(null);
    }

    /**
     * 保存用户（新增/修改）
     */
    public void saveUser(User user) {
        // 更新缓存：先移除旧的，再添加新的
        users.removeIf(u -> u.getUsername().equals(user.getUsername()));
        users.add(user);

        // 立即保存到文件
        try {
            File usersDir = new File(dataFolder, "users");
            if (!usersDir.exists()) usersDir.mkdirs();
            
            File userFile = new File(usersDir, user.getUsername() + ".json");
            try (FileOutputStream fos = new FileOutputStream(userFile)) {
                String json = GsonUtils.getGson().toJson(user);
                fos.write(json.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            plugin.getLogger().severe("❌ 保存用户失败：" + e.getMessage());
        }
    }

    /**
     * 删除用户
     */
    public boolean deleteUser(String username) {
        boolean removed = users.removeIf(user -> user.getUsername().equals(username));
        if (removed) {
            // 删除文件
            File userFile = new File(dataFolder, "users/" + username + ".json");
            if (userFile.exists()) {
                userFile.delete();
            }
        }
        return removed;
    }

    /**
     * 获取所有用户
     */
    public List<User> getUsers() {
        return new ArrayList<>(users);
    }

    /**
     * 根据QQ OpenID查找用户
     */
    public User getUserByQQOpenId(String qqOpenId) {
        return users.stream()
                    .filter(user -> user.getQqBinding() != null && qqOpenId.equals(user.getQqBinding().getQqOpenId()))
                    .findFirst()
                    .orElse(null);
    }

    /**
     * 根据QQ UnionID查找用户
     */
    public User getUserByQQUnionId(String qqUnionId) {
        return users.stream()
                    .filter(user -> user.getQqBinding() != null && qqUnionId.equals(user.getQqBinding().getQqUnionId()))
                    .findFirst()
                    .orElse(null);
    }

    // ====================== 邮箱验证码数据操作 ======================
    /**
     * 加载邮箱验证码数据
     */
    private void loadEmailVerificationCodes() throws IOException {
        File codesDir = new File(dataFolder, "email_codes");
        if (!codesDir.exists()) return;

        Files.list(Paths.get(codesDir.getPath()))
             .filter(path -> path.toString().endsWith(".json"))
             .forEach(path -> {
                 try (FileInputStream fis = new FileInputStream(path.toFile())) {
                     String json = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
                     EmailVerificationCode code = GsonUtils.getGson().fromJson(json, EmailVerificationCode.class);
                     if (code != null && code.getId() > 0) {
                         // 只加载未过期的验证码
                         if (!code.isExpired()) {
                             emailVerificationCodes.add(code);
                         }
                     }
                 } catch (Exception e) {
                     plugin.getLogger().warning("⚠️ 加载邮箱验证码文件失败：" + path.getFileName() + " → " + e.getMessage());
                 }
             });
    }

    /**
     * 保存所有邮箱验证码数据
     */
    private void saveEmailVerificationCodes() throws IOException {
        File codesDir = new File(dataFolder, "email_codes");
        if (!codesDir.exists()) codesDir.mkdirs();

        // 清空旧文件
        Files.list(Paths.get(codesDir.getPath()))
             .filter(path -> path.toString().endsWith(".json"))
             .forEach(path -> {
                 try {
                     Files.delete(path);
                 } catch (IOException e) {
                     plugin.getLogger().warning("⚠️ 删除旧邮箱验证码文件失败：" + path.getFileName());
                 }
             });

        // 保存新数据（按UUID命名文件）
        for (EmailVerificationCode code : emailVerificationCodes) {
            // 只保存未过期的验证码
            if (!code.isExpired()) {
                File codeFile = new File(codesDir, code.getIdString() + ".json");
                try (FileOutputStream fos = new FileOutputStream(codeFile)) {
                    String json = GsonUtils.getGson().toJson(code);
                    fos.write(json.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }

    /**
     * 根据邮箱和类型获取验证码
     */
    public EmailVerificationCode getEmailVerificationCode(String email, EmailVerificationCode.CodeType type) {
        return emailVerificationCodes.stream()
                                     .filter(code -> code.getEmail().equals(email) && code.getType() == type && !code.isExpired())
                                     .findFirst()
                                     .orElse(null);
    }

    /**
     * 保存邮箱验证码
     */
    public void saveEmailVerificationCode(EmailVerificationCode code) {
        // 更新缓存：先移除旧的同类型验证码，再添加新的
        emailVerificationCodes.removeIf(c -> c.getEmail().equals(code.getEmail()) && c.getType() == code.getType());
        emailVerificationCodes.add(code);

        // 立即保存到文件
        try {
            File codesDir = new File(dataFolder, "email_codes");
            if (!codesDir.exists()) codesDir.mkdirs();
            
            File codeFile = new File(codesDir, code.getIdString() + ".json");
            try (FileOutputStream fos = new FileOutputStream(codeFile)) {
                String json = GsonUtils.getGson().toJson(code);
                fos.write(json.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            plugin.getLogger().severe("❌ 保存邮箱验证码失败：" + e.getMessage());
        }
    }

    /**
     * 删除邮箱验证码
     */
    public boolean deleteEmailVerificationCode(String id) {
        boolean removed = emailVerificationCodes.removeIf(code -> code.getIdString().equals(id));
        if (removed) {
            // 删除文件
            File codeFile = new File(dataFolder, "email_codes/" + id + ".json");
            if (codeFile.exists()) {
                codeFile.delete();
            }
        }
        return removed;
    }

    /**
     * 清理过期的邮箱验证码
     */
    public void cleanExpiredEmailVerificationCodes() {
        List<EmailVerificationCode> expiredCodes = emailVerificationCodes.stream()
                                                                       .filter(EmailVerificationCode::isExpired)
                                                                       .collect(Collectors.toList());
        
        for (EmailVerificationCode code : expiredCodes) {
            deleteEmailVerificationCode(code.getIdString());
        }
    }
}