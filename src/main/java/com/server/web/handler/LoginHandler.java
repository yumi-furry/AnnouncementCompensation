package com.server.web.handler;

import com.server.AnnouncementCompensationPlugin;
import com.server.data.model.Admin;
import com.server.util.BCryptUtils;
import com.server.util.GsonUtils;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 管理员登录API处理器
 * 处理 /api/login POST请求，验证用户名密码，生成登录Token
 */
public class LoginHandler implements HttpHandler {
    private final AnnouncementCompensationPlugin plugin;
    // 存储登录Token（内存级，插件重启失效，生产可改为持久化）
    private final Map<String, Admin> tokenMap = new HashMap<>();

    public LoginHandler(AnnouncementCompensationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        // 仅允许POST请求
        if (!"POST".equals(exchange.getRequestMethod().toString())) {
            sendErrorResponse(exchange, StatusCodes.METHOD_NOT_ALLOWED, "仅支持POST请求");
            return;
        }

        // 解析请求体为Admin登录参数
        String requestBody = new String(exchange.getInputStream().readAllBytes());
        Admin loginAdmin = GsonUtils.getGson().fromJson(requestBody, Admin.class);
        
        // 参数校验
        if (loginAdmin.getUsername() == null || loginAdmin.getPassword() == null) {
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
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "登录成功");
        response.put("token", token);
        response.put("username", realAdmin.getUsername());
        response.put("permissions", realAdmin.getPermissions());

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
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        
        exchange.setStatusCode(statusCode);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json;charset=UTF-8");
        exchange.getResponseSender().send(GsonUtils.getGson().toJson(response));
    }
}