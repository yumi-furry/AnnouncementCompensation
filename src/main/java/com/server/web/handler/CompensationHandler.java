package com.server.web.handler;

import com.server.AnnouncementCompensationPlugin;
import com.server.data.model.Compensation;
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
 * 补偿管理API处理器
 * 处理 /api/compensation GET/POST/DELETE请求，增删改查补偿
 */
public class CompensationHandler implements HttpHandler {
    private final AnnouncementCompensationPlugin plugin;
    private final LoginHandler loginHandler;

    /**
     * 构造方法：从插件主类获取LoginHandler实例（修复核心）
     * @param plugin 插件主类实例
     */
    public CompensationHandler(AnnouncementCompensationPlugin plugin) {
        this.plugin = plugin;
        // 修复点：删除错误的getHandlerByPath调用，从插件主类获取LoginHandler
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

        // 2. 校验补偿管理权限
        if (!WebPermission.COMPENSATION.hasPermission(admin.getPermissions())) {
            sendErrorResponse(exchange, StatusCodes.FORBIDDEN, "无补偿管理权限");
            return;
        }

        // 3. 处理请求方法
        String method = exchange.getRequestMethod().toString();
        switch (method) {
            case "GET":
                handleGetCompensations(exchange);
                break;
            case "POST":
                handleAddCompensation(exchange);
                break;
            case "DELETE":
                handleDeleteCompensation(exchange);
                break;
            default:
                sendErrorResponse(exchange, StatusCodes.METHOD_NOT_ALLOWED, "仅支持GET/POST/DELETE请求");
        }
    }

    /**
     * 获取所有补偿
     */
    private void handleGetCompensations(HttpServerExchange exchange) {
        List<Compensation> compensations = plugin.getDataManager().getAllCompensations();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", compensations);
        sendSuccessResponse(exchange, response);
    }

    /**
     * 添加新补偿
     */
    private void handleAddCompensation(HttpServerExchange exchange) {
        try {
            String requestBody = new String(exchange.getInputStream().readAllBytes());
            Compensation compensation = GsonUtils.getGson().fromJson(requestBody, Compensation.class);
            
            if (compensation.getName() == null || compensation.getDescription() == null) {
                sendErrorResponse(exchange, StatusCodes.BAD_REQUEST, "补偿名称/说明不能为空");
                return;
            }

            plugin.getDataManager().saveCompensation(compensation);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "补偿添加成功");
            response.put("data", compensation);
            sendSuccessResponse(exchange, response);
        } catch (Exception e) {
            sendErrorResponse(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "添加补偿失败：" + e.getMessage());
        }
    }

    /**
     * 删除补偿
     */
    private void handleDeleteCompensation(HttpServerExchange exchange) {
        var param = exchange.getQueryParameters().get("id");
        if (param == null || param.isEmpty()) {
            sendErrorResponse(exchange, StatusCodes.BAD_REQUEST, "补偿ID不能为空");
            return;
        }
        String id = param.getFirst();

        boolean deleted = plugin.getDataManager().deleteCompensation(id);
        if (deleted) {
            sendSuccessResponse(exchange, Map.of("success", true, "message", "补偿删除成功"));
        } else {
            sendErrorResponse(exchange, StatusCodes.NOT_FOUND, "补偿不存在");
        }
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