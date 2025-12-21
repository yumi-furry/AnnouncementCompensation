package com.server.database;

import com.server.AnnouncementCompensationPlugin;
import com.server.data.model.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据库数据管理器
 * 处理所有与数据库相关的CRUD操作
 */
public class DatabaseDataManager {
    private final AnnouncementCompensationPlugin plugin;
    private final DatabaseManager databaseManager;
    private final Gson gson;

    public DatabaseDataManager(AnnouncementCompensationPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.gson = new Gson();
    }

    // ====================== 管理员操作 ======================
    
    public List<Admin> loadAdmins() {
        List<Admin> admins = new ArrayList<>();
        if (!databaseManager.isUsingDatabase()) {
            return admins;
        }

        String sql = "SELECT id, username, password_hash, permissions FROM admins ORDER BY id";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Admin admin = new Admin();
                admin.setId(rs.getInt("id"));
                admin.setUsername(rs.getString("username"));
                admin.setPasswordHash(rs.getString("password_hash"));
                
                // 解析权限JSON
                String permissionsJson = rs.getString("permissions");
                if (permissionsJson != null) {
                    Type listType = new TypeToken<List<String>>(){}.getType();
                    List<String> permissions = gson.fromJson(permissionsJson, listType);
                    admin.setPermissions(permissions);
                }
                
                admins.add(admin);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ 加载管理员数据失败：" + e.getMessage());
            e.printStackTrace();
        }
        
        return admins;
    }

    public void saveAdmins(List<Admin> admins) {
        if (!databaseManager.isUsingDatabase()) {
            return;
        }

        try (Connection conn = databaseManager.getConnection()) {
            // 先删除所有现有数据
            conn.createStatement().execute("DELETE FROM admins");
            
            // 插入新数据
            String sql = "INSERT INTO admins (username, password_hash, permissions) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (Admin admin : admins) {
                    stmt.setString(1, admin.getUsername());
                    stmt.setString(2, admin.getPasswordHash());
                    stmt.setString(3, gson.toJson(admin.getPermissions()));
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ 保存管理员数据失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    public Admin getAdminByUsername(String username) {
        if (!databaseManager.isUsingDatabase()) {
            return null;
        }

        String sql = "SELECT id, username, password_hash, permissions FROM admins WHERE username = ?";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Admin admin = new Admin();
                    admin.setId(rs.getInt("id"));
                    admin.setUsername(rs.getString("username"));
                    admin.setPasswordHash(rs.getString("password_hash"));
                    
                    // 解析权限JSON
                    String permissionsJson = rs.getString("permissions");
                    if (permissionsJson != null) {
                        Type listType = new TypeToken<List<String>>(){}.getType();
                        List<String> permissions = gson.fromJson(permissionsJson, listType);
                        admin.setPermissions(permissions);
                    }
                    
                    return admin;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ 查询管理员数据失败：" + e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }

    // ====================== 公告操作 ======================
    
    public List<Announcement> loadAnnouncements() {
        List<Announcement> announcements = new ArrayList<>();
        if (!databaseManager.isUsingDatabase()) {
            return announcements;
        }

        String sql = "SELECT id, title, content, author, created_at, updated_at FROM announcements ORDER BY id DESC";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Announcement announcement = new Announcement();
                announcement.setId(rs.getInt("id"));
                announcement.setTitle(rs.getString("title"));
                announcement.setContent(rs.getString("content"));
                announcement.setAuthor(rs.getString("author"));
                announcement.setCreatedAt(rs.getString("created_at"));
                announcement.setUpdatedAt(rs.getString("updated_at"));
                
                announcements.add(announcement);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ 加载公告数据失败：" + e.getMessage());
            e.printStackTrace();
        }
        
        return announcements;
    }

    public void saveAnnouncements(List<Announcement> announcements) {
        if (!databaseManager.isUsingDatabase()) {
            return;
        }

        try (Connection conn = databaseManager.getConnection()) {
            // 先删除所有现有数据
            conn.createStatement().execute("DELETE FROM announcements");
            
            // 插入新数据
            String sql = "INSERT INTO announcements (title, content, author) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (Announcement announcement : announcements) {
                    stmt.setString(1, announcement.getTitle());
                    stmt.setString(2, announcement.getContent());
                    stmt.setString(3, announcement.getAuthor());
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ 保存公告数据失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    // ====================== 补偿操作 ======================
    
    public List<Compensation> loadCompensations() {
        List<Compensation> compensations = new ArrayList<>();
        if (!databaseManager.isUsingDatabase()) {
            return compensations;
        }

        String sql = "SELECT id, title, description, items, author, created_at, updated_at FROM compensations ORDER BY id DESC";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Compensation compensation = new Compensation();
                compensation.setId(rs.getInt("id"));
                compensation.setTitle(rs.getString("title"));
                compensation.setDescription(rs.getString("description"));
                
                // 解析物品JSON
                String itemsJson = rs.getString("items");
                if (itemsJson != null) {
                    Type listType = new TypeToken<List<String>>(){}.getType();
                    List<String> items = gson.fromJson(itemsJson, listType);
                    compensation.setItemsFromStrings(items);
                }
                
                compensation.setAuthor(rs.getString("author"));
                compensation.setCreatedAt(rs.getString("created_at"));
                compensation.setUpdatedAt(rs.getString("updated_at"));
                
                compensations.add(compensation);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ 加载补偿数据失败：" + e.getMessage());
            e.printStackTrace();
        }
        
        return compensations;
    }

    public void saveCompensations(List<Compensation> compensations) {
        if (!databaseManager.isUsingDatabase()) {
            return;
        }

        try (Connection conn = databaseManager.getConnection()) {
            // 先删除所有现有数据
            conn.createStatement().execute("DELETE FROM compensations");
            
            // 插入新数据
            String sql = "INSERT INTO compensations (title, description, items, author) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (Compensation compensation : compensations) {
                    stmt.setString(1, compensation.getTitle());
                    stmt.setString(2, compensation.getDescription());
                    stmt.setString(3, gson.toJson(compensation.getItems()));
                    stmt.setString(4, compensation.getAuthor());
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ 保存补偿数据失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    // ====================== 领取日志操作 ======================
    
    public List<ClaimLog> loadClaimLogs() {
        List<ClaimLog> claimLogs = new ArrayList<>();
        if (!databaseManager.isUsingDatabase()) {
            return claimLogs;
        }

        String sql = "SELECT id, compensation_id, player_name, player_uuid, claim_time FROM claim_logs ORDER BY id DESC";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                ClaimLog claimLog = new ClaimLog();
                claimLog.setId(rs.getInt("id"));
                claimLog.setCompensationId(rs.getInt("compensation_id"));
                claimLog.setPlayerName(rs.getString("player_name"));
                claimLog.setPlayerUuid(rs.getString("player_uuid"));
                claimLog.setClaimTime(rs.getString("claim_time"));
                
                claimLogs.add(claimLog);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ 加载领取日志数据失败：" + e.getMessage());
            e.printStackTrace();
        }
        
        return claimLogs;
    }

    public void saveClaimLogs(List<ClaimLog> claimLogs) {
        if (!databaseManager.isUsingDatabase()) {
            return;
        }

        try (Connection conn = databaseManager.getConnection()) {
            // 先删除所有现有数据
            conn.createStatement().execute("DELETE FROM claim_logs");
            
            // 插入新数据
            String sql = "INSERT INTO claim_logs (compensation_id, player_name, player_uuid) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (ClaimLog claimLog : claimLogs) {
                    stmt.setInt(1, claimLog.getCompensationIdInt());
                    stmt.setString(2, claimLog.getPlayerName());
                    stmt.setString(3, claimLog.getPlayerUuid());
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ 保存领取日志数据失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    // ====================== 白名单操作 ======================
    
    public List<WhitelistEntry> loadWhitelistEntries() {
        List<WhitelistEntry> whitelistEntries = new ArrayList<>();
        if (!databaseManager.isUsingDatabase()) {
            return whitelistEntries;
        }

        String sql = "SELECT id, player_name, player_uuid, added_by, reason, created_at FROM whitelist_entries ORDER BY id DESC";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                WhitelistEntry whitelistEntry = new WhitelistEntry();
                whitelistEntry.setId(rs.getInt("id"));
                whitelistEntry.setPlayerName(rs.getString("player_name"));
                whitelistEntry.setPlayerUuid(rs.getString("player_uuid"));
                whitelistEntry.setAddedBy(rs.getString("added_by"));
                whitelistEntry.setReason(rs.getString("reason"));
                whitelistEntry.setCreatedAt(rs.getString("created_at"));
                
                whitelistEntries.add(whitelistEntry);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ 加载白名单数据失败：" + e.getMessage());
            e.printStackTrace();
        }
        
        return whitelistEntries;
    }

    public void saveWhitelistEntries(List<WhitelistEntry> whitelistEntries) {
        if (!databaseManager.isUsingDatabase()) {
            return;
        }

        try (Connection conn = databaseManager.getConnection()) {
            // 先删除所有现有数据
            conn.createStatement().execute("DELETE FROM whitelist_entries");
            
            // 插入新数据
            String sql = "INSERT INTO whitelist_entries (player_name, player_uuid, added_by, reason) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (WhitelistEntry whitelistEntry : whitelistEntries) {
                    stmt.setString(1, whitelistEntry.getPlayerName());
                    stmt.setString(2, whitelistEntry.getPlayerUuid());
                    stmt.setString(3, whitelistEntry.getAddedBy());
                    stmt.setString(4, whitelistEntry.getReason());
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ 保存白名单数据失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    // ====================== 用户操作 ======================
    
    public List<User> loadUsers() {
        List<User> users = new ArrayList<>();
        if (!databaseManager.isUsingDatabase()) {
            return users;
        }

        String sql = "SELECT id, username, email, password_hash, qq_number, minecraft_uuid, is_verified, created_at, updated_at FROM users ORDER BY id DESC";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                User user = new User();
                user.setId(rs.getInt("id"));
                user.setUsername(rs.getString("username"));
                user.setEmail(rs.getString("email"));
                user.setPasswordHash(rs.getString("password_hash"));
                user.setQqNumber(rs.getString("qq_number"));
                user.setMinecraftUuid(rs.getString("minecraft_uuid"));
                user.setVerified(rs.getBoolean("is_verified"));
                user.setCreatedAt(rs.getString("created_at"));
                user.setUpdatedAt(rs.getString("updated_at"));
                
                users.add(user);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ 加载用户数据失败：" + e.getMessage());
            e.printStackTrace();
        }
        
        return users;
    }

    public void saveUsers(List<User> users) {
        if (!databaseManager.isUsingDatabase()) {
            return;
        }

        try (Connection conn = databaseManager.getConnection()) {
            // 先删除所有现有数据
            conn.createStatement().execute("DELETE FROM users");
            
            // 插入新数据
            String sql = "INSERT INTO users (username, email, password_hash, qq_number, minecraft_uuid, is_verified) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (User user : users) {
                    stmt.setString(1, user.getUsername());
                    stmt.setString(2, user.getEmail());
                    stmt.setString(3, user.getPasswordHash());
                    stmt.setString(4, user.getQqNumber());
                    stmt.setString(5, user.getMinecraftUuid());
                    stmt.setBoolean(6, user.isVerified());
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ 保存用户数据失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    // ====================== 邮箱验证操作 ======================
    
    public List<EmailVerificationCode> loadEmailVerificationCodes() {
        List<EmailVerificationCode> verificationCodes = new ArrayList<>();
        if (!databaseManager.isUsingDatabase()) {
            return verificationCodes;
        }

        String sql = "SELECT id, email, code, expires_at, created_at FROM email_verification_codes ORDER BY id DESC";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                EmailVerificationCode verificationCode = new EmailVerificationCode();
                verificationCode.setId(rs.getString("id"));
                verificationCode.setEmail(rs.getString("email"));
                verificationCode.setCode(rs.getString("code"));
                verificationCode.setExpiresAt(rs.getString("expires_at"));
                verificationCode.setCreateTime(rs.getString("created_at"));
                
                verificationCodes.add(verificationCode);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ 加载邮箱验证码数据失败：" + e.getMessage());
            e.printStackTrace();
        }
        
        return verificationCodes;
    }

    public void saveEmailVerificationCodes(List<EmailVerificationCode> verificationCodes) {
        if (!databaseManager.isUsingDatabase()) {
            return;
        }

        try (Connection conn = databaseManager.getConnection()) {
            // 先删除所有现有数据
            conn.createStatement().execute("DELETE FROM email_verification_codes");
            
            // 插入新数据
            String sql = "INSERT INTO email_verification_codes (email, code, expires_at) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (EmailVerificationCode verificationCode : verificationCodes) {
                    stmt.setString(1, verificationCode.getEmail());
                    stmt.setString(2, verificationCode.getCode());
                    stmt.setString(3, verificationCode.getExpiresAt());
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ 保存邮箱验证码数据失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    // ====================== 白名单启用状态操作 ======================
    
    public boolean loadWhitelistEnabledStatus() {
        if (!databaseManager.isUsingDatabase()) {
            return false;
        }

        String sql = "SELECT setting_value FROM settings WHERE setting_key = 'whitelist_enabled'";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                String value = rs.getString("setting_value");
                return "true".equalsIgnoreCase(value);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ 加载白名单启用状态失败：" + e.getMessage());
            e.printStackTrace();
        }
        
        return false;
    }

    public void saveWhitelistEnabledStatus(boolean enabled) {
        if (!databaseManager.isUsingDatabase()) {
            return;
        }

        String sql = "INSERT INTO settings (setting_key, setting_value) VALUES ('whitelist_enabled', ?) " +
                     "ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, enabled ? "true" : "false");
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ 保存白名单启用状态失败：" + e.getMessage());
            e.printStackTrace();
        }
    }
}