package com.server.web.handler;

import com.server.AnnouncementCompensationPlugin;
import com.server.data.model.Admin;
import com.server.util.BCryptUtils;
import com.server.util.GsonUtils;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;


import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理员登录API处理器（含调试日志）
 */
public class LoginHandler implements HttpHandler {
    private final AnnouncementCompensationPlugin plugin;
    // 线程安全的 Token 存储
    private final Map<String, Admin> tokenMap = new ConcurrentHashMap<>();

    public LoginHandler(AnnouncementCompensationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        // 仅允许 POST
        if (!"POST".equals(exchange.getRequestMethod().toString())) {
            sendErrorResponse(exchange, StatusCodes.METHOD_NOT_ALLOWED, "仅支持POST请求");
            return;
        }

        // 异步接收完整请求体
        exchange.getRequestReceiver().receiveFullString((ex, message) -> {
            // 调试：打印来源、关键头与请求体长度（上线后移除明文日志）
            String remote = ex.getSourceAddress() != null ? ex.getSourceAddress().toString() : "unknown";
            String ct = ex.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
            String cl = ex.getRequestHeaders().getFirst(Headers.CONTENT_LENGTH);
            plugin.getLogger().info("🔔 /api/login 请求来自: " + remote + " Content-Type=" + ct + " Content-Length=" + cl);
            plugin.getLogger().info("🔎 请求体长度=" + (message != null ? message.length() : 0));
            plugin.getLogger().fine("🔐 请求体原文（调试，请删除）： " + message);

            // 收到完整请求体后调度到工作线程处理
            ex.dispatch(() -> {
                try {
                    processLoginWithBody(ex, message);
                } catch (Exception e) {
                    plugin.getLogger().severe("❌ LoginHandler 处理失败：" + e.getMessage());
                    try {
                        sendErrorResponse(ex, StatusCodes.INTERNAL_SERVER_ERROR, "服务器内部错误");
                    } catch (Exception ignored) {}
                }
            });
        }, (ex, exception) -> {
            plugin.getLogger().warning("⚠️ 接收请求体失败：" + exception.getMessage());
            try {
                sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "无法读取请求体");
            } catch (Exception ignored) {}
        });
    }

    // 在工作线程中执行的实际处理逻辑，收到完整请求体字符串
    private void processLoginWithBody(HttpServerExchange exchange, String requestBody) throws Exception {
        if (requestBody == null || requestBody.trim().isEmpty()) {
            plugin.getLogger().info("⚠️ 登录失败：请求体为空或仅空白");
            sendErrorResponse(exchange, StatusCodes.BAD_REQUEST, "请求体为空");
            return;
        }

        // 解析JSON
        Admin loginAdmin;
        try {
            loginAdmin = GsonUtils.getGson().fromJson(requestBody, Admin.class);
        } catch (Exception e) {
            plugin.getLogger().warning("⚠️ JSON 解析失败：" + e.getMessage() + " 原始请求体：" + requestBody);
            sendErrorResponse(exchange, StatusCodes.BAD_REQUEST, "请求体格式不正确（非JSON）");
            return;
        }

        // 参数校验
        if (loginAdmin == null || loginAdmin.getUsername() == null || loginAdmin.getPassword() == null) {
            plugin.getLogger().info("⚠️ 登录失败：用户名/密码为空，解析结果：" + loginAdmin);
            sendErrorResponse(exchange, StatusCodes.BAD_REQUEST, "用户名/密码不能为空");
            return;
        }

        // 从数据管理器获取管理员信息
        Admin realAdmin = plugin.getDataManager().getAdminByUsername(loginAdmin.getUsername());
        if (realAdmin == null) {
            sendErrorResponse(exchange, StatusCodes.UNAUTHORIZED, "用户名不存在");
            return;
        }

        // 验证密码（BCrypt匹配）
        if (!BCryptUtils.verify(loginAdmin.getPassword(), realAdmin.getPasswordHash())) {
            sendErrorResponse(exchange, StatusCodes.UNAUTHORIZED, "密码错误");
            return;
        }

        // 生成登录Token（UUID随机生成）
        String token = UUID.randomUUID().toString().replace("-", "");
        tokenMap.put(token, realAdmin);

        // 构建成功响应
        Map<String, Object> response = Map.of(
                "success", true,
                "message", "登录成功",
                "token", token,
                "username", realAdmin.getUsername(),
                "permissions", realAdmin.getPermissions()
        );

        sendSuccessResponse(exchange, response);
        plugin.getLogger().info("管理员 " + realAdmin.getUsername() + " 登录Web面板");
    }

    /**
     * 验证Token是否有效（供其他Handler调用）
     */
    public Admin validateToken(String token) {
        return tokenMap.get(token);
    }

    /**
     * 验证Token是否有效（布尔值版本）
     */
    public boolean isValidToken(String token) {
        return tokenMap.containsKey(token);
    }

    /**
     * 检查Token是否为管理员Token
     */
    public boolean isAdminToken(String token) {
        Admin admin = tokenMap.get(token);
        return admin != null;
    }

    /**
     * 获取管理员权限列表
     */
    public java.util.List<String> getAdminPermissions(String token) {
        Admin admin = tokenMap.get(token);
        return admin != null ? admin.getPermissions() : java.util.Collections.emptyList();
    }

    /**
     * 退出登录（移除Token）
     */
    public void logout(String token) {
        tokenMap.remove(token);
    }

    // ====================== 响应工具方法 ======================
    private void sendSuccessResponse(HttpServerExchange exchange, Object data) {
        exchange.setStatusCode(StatusCodes.OK);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json;charset=UTF-8");
        exchange.getResponseSender().send(GsonUtils.getGson().toJson(data));
    }

    private void sendErrorResponse(HttpServerExchange exchange, int statusCode, String message) {
        Map<String, Object> response = Map.of(
                "success", false,
                "message", message
        );

        exchange.setStatusCode(statusCode);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json;charset=UTF-8");
        exchange.getResponseSender().send(GsonUtils.getGson().toJson(response));
    }
}