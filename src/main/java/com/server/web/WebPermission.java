package com.server.web;

/**
 * Web面板权限枚举
 * 与plugin.yml中的权限节点一一对应，简化权限校验逻辑
 * 示例：WebPermission.ANNOUNCEMENT.getPermission() → "ac.web.announcement"
 */
public enum WebPermission {
    // 公告管理权限
    ANNOUNCEMENT("ac.web.announcement"),
    // 补偿管理权限
    COMPENSATION("ac.web.compensation"),
    // 白名单管理权限
    WHITELIST("ac.web.whitelist"),
    // 领取日志查看权限
    LOG("ac.web.log"),
    // 所有Web权限（通配符）
    ALL("ac.web.*");

    // 权限节点字符串（与plugin.yml一致）
    private final String permission;

    /**
     * 构造方法：绑定权限节点
     * @param permission 权限节点字符串
     */
    WebPermission(String permission) {
        this.permission = permission;
    }

    /**
     * 获取权限节点字符串
     * @return 完整权限节点（如ac.web.announcement）
     */
    public String getPermission() {
        return permission;
    }

    /**
     * 校验管理员是否拥有指定权限（简化调用）
     * @param adminPermissions 管理员的权限列表
     * @return true=拥有权限，false=无权限
     */
    public boolean hasPermission(java.util.List<String> adminPermissions) {
        if (adminPermissions == null || adminPermissions.isEmpty()) {
            return false;
        }
        // 拥有全权限或指定权限即通过
        return adminPermissions.contains(ALL.getPermission()) 
                || adminPermissions.contains(this.permission);
    }
}