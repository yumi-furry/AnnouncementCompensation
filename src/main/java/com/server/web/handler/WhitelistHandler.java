package com.server.web.handler;

import com.google.gson.reflect.TypeToken;
import com.server.AnnouncementCompensationPlugin;
import com.server.data.model.WhitelistEntry;
import com.server.web.WebPermission;
import com.server.util.GsonUtils;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 白名单管理API处理器
 * 处理 /api/whitelist GET/POST/DELETE请求，增删改查白名单、切换启用状态
 */
public class WhitelistHandler implements HttpHandler {
    private final AnnouncementCompensationPlugin plugin;
    private final LoginHandler loginHandler;
    // 定义Map<String, String>的TypeToken，解决泛型擦除问题
    private static final Type STRING_STRING_MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    public WhitelistHandler(AnnouncementCompensationPlugin plugin) {
        this.plugin = plugin;
        // 修复点1：从插件主类获取LoginHandler实例（消除getHandlerByPath错误）
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

        // 2. 校验白名单管理权限
        if (!WebPermission.WHITELIST.hasPermission(admin.getPermissions())) {
            sendErrorResponse(exchange, StatusCodes.FORBIDDEN, "无白名单管理权限");
            return;
        }

        // 3. 处理请求方法
        String method = exchange.getRequestMethod().toString();
        switch (method) {
            case "GET":
                handleGetWhitelist(exchange);
                break;
            case "POST":
                handleWhitelistAction(exchange);
                break;
            case "DELETE":
                handleDeleteWhitelist(exchange);
                break;
            default:
                sendErrorResponse(exchange, StatusCodes.METHOD_NOT_ALLOWED, "仅支持GET/POST/DELETE请求");
        }
    }

    /**
     * 获取白名单列表及启用状态
     */
    private void handleGetWhitelist(HttpServerExchange exchange) {
        List<WhitelistEntry> whitelist = plugin.getDataManager().getAllWhitelistEntries();
        boolean enabled = plugin.getDataManager().isWhitelistEnabled();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("enabled", enabled);
        response.put("data", whitelist);
        sendSuccessResponse(exchange, response);
    }

    /**
     * 处理白名单操作（添加/切换启用状态）
     */
    private void handleWhitelistAction(HttpServerExchange exchange) {
        try {
            String requestBody = new String(exchange.getInputStream().readAllBytes());
            // 修复点2：使用TypeToken指定泛型类型，彻底消除"未经检查的转换"警告
            Map<String, String> params = GsonUtils.getGson().fromJson(requestBody, STRING_STRING_MAP_TYPE);
            
            String action = params.get("action");
            if (action == null) {
                sendErrorResponse(exchange, StatusCodes.BAD_REQUEST, "操作类型不能为空（add/toggle）");
                return;
            }

            if ("add".equals(action)) {
                // 添加白名单
                String uuid = params.get("uuid");
                String name = params.get("name");
                if (uuid == null || name == null) {
                    sendErrorResponse(exchange, StatusCodes.BAD_REQUEST, "玩家UUID/名称不能为空");
                    return;
                }

                WhitelistEntry entry = new WhitelistEntry(uuid, name);
                plugin.getDataManager().addWhitelistEntry(entry);
                sendSuccessResponse(exchange, Map.of("success", true, "message", "白名单添加成功"));
            } else if ("toggle".equals(action)) {
                // 切换启用状态
                String enabledStr = params.get("enabled");
                if (enabledStr == null) {
                    sendErrorResponse(exchange, StatusCodes.BAD_REQUEST, "启用状态不能为空（true/false）");
                    return;
                }
                boolean enabled = Boolean.parseBoolean(enabledStr);
                plugin.getDataManager().setWhitelistEnabled(enabled);
                sendSuccessResponse(exchange, Map.of("success", true, "message", "白名单已" + (enabled ? "启用" : "禁用")));
            } else {
                sendErrorResponse(exchange, StatusCodes.BAD_REQUEST, "不支持的操作类型（仅add/toggle）");
            }
        } catch (Exception e) {
            sendErrorResponse(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "操作白名单失败：" + e.getMessage());
        }
    }

    /**
     * 删除白名单
     */
    private void handleDeleteWhitelist(HttpServerExchange exchange) {
        String uuid = exchange.getQueryParameters().get("uuid").getFirst();
        if (uuid == null) {
            sendErrorResponse(exchange, StatusCodes.BAD_REQUEST, "玩家UUID不能为空");
            return;
        }

        boolean deleted = plugin.getDataManager().deleteWhitelistEntry(uuid);
        if (deleted) {
            sendSuccessResponse(exchange, Map.of("success", true, "message", "白名单删除成功"));
        } else {
            sendErrorResponse(exchange, StatusCodes.NOT_FOUND, "白名单不存在");
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