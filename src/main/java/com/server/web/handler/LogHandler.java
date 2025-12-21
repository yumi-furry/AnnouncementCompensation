package com.server.web.handler;

import com.server.AnnouncementCompensationPlugin;
import com.server.data.model.ClaimLog;
import com.server.web.WebPermission;
import com.server.util.GsonUtils;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 补偿领取日志API处理器
 * 处理 /api/log GET请求，查询领取日志
 */
public class LogHandler implements HttpHandler {
    private final AnnouncementCompensationPlugin plugin;
    private final LoginHandler loginHandler;

    /**
     * 构造方法：从插件主类获取LoginHandler实例（修复核心）
     * @param plugin 插件主类实例
     */
    public LogHandler(AnnouncementCompensationPlugin plugin) {
        this.plugin = plugin;
        // 修复点：删除getHandlerByPath，从插件主类直接获取LoginHandler实例
        this.loginHandler = plugin.getLoginHandler();
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        // 1. 验证Token
        String token = exchange.getRequestHeaders().getFirst("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            sendErrorResponse(exchange, StatusCodes.UNAUTHORIZED, "未登录，请先登录");
            return;
        }
        token = token.replace("Bearer ", "");
        var admin = loginHandler.validateToken(token);
        if (admin == null) {
            sendErrorResponse(exchange, StatusCodes.UNAUTHORIZED, "Token失效，请重新登录");
            return;
        }

        // 2. 校验日志查看权限
        if (!WebPermission.LOG.hasPermission(admin.getPermissions())) {
            sendErrorResponse(exchange, StatusCodes.FORBIDDEN, "无日志查看权限");
            return;
        }

        // 3. 仅支持GET请求
        if (!"GET".equals(exchange.getRequestMethod().toString())) {
            sendErrorResponse(exchange, StatusCodes.METHOD_NOT_ALLOWED, "仅支持GET请求");
            return;
        }

        // 4. 获取所有领取日志
        handleGetLogs(exchange);
    }

    /**
     * 获取所有补偿领取日志
     */
    private void handleGetLogs(HttpServerExchange exchange) {
        List<ClaimLog> logs = plugin.getDataManager().getAllClaimLogs();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", logs);
        sendSuccessResponse(exchange, response);
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