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
        // 修复点1：从插件主类获取LoginHandler实例
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
     * 使用非阻塞接收并在工作线程处理，避免在 IO 线程做阻塞 IO
     */
    private void handleWhitelistAction(HttpServerExchange exchange) {
        // 使用异步接收完整请求体
        exchange.getRequestReceiver().receiveFullString((ex, message) -> {
            // 收到完整请求体后调度到工作线程处理（安全地做耗时IO与响应）
            ex.dispatch(() -> {
                try {
                    if (message == null || message.trim().isEmpty()) {
                        sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "请求体为空");
                        return;
                    }

                    Map<String, String> params;
                    try {
                        params = GsonUtils.getGson().fromJson(message, STRING_STRING_MAP_TYPE);
                    } catch (Exception je) {
                        plugin.getLogger().warning("⚠️ 解析白名单请求JSON失败：" + je.getMessage());
                        sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "请求体格式不正确（非JSON）");
                        return;
                    }

                    if (params == null) {
                        sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "请求体格式不正确（空对象）");
                        return;
                    }

                    String action = params.get("action");
                    if (action == null) {
                        sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "操作类型不能为空（add/toggle）");
                        return;
                    }

                    if ("add".equals(action)) {
                        String uuid = params.get("uuid");
                        String name = params.get("name");
                        if (uuid == null || name == null || uuid.trim().isEmpty() || name.trim().isEmpty()) {
                            sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "玩家UUID/名称不能为空");
                            return;
                        }
                        WhitelistEntry entry = new WhitelistEntry(uuid, name);
                        plugin.getDataManager().addWhitelistEntry(entry);
                        sendSuccessResponse(ex, Map.of("success", true, "message", "白名单添加成功"));
                    } else if ("toggle".equals(action)) {
                        String enabledStr = params.get("enabled");
                        if (enabledStr == null) {
                            sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "启用状态不能为空（true/false）");
                            return;
                        }
                        boolean enabled = Boolean.parseBoolean(enabledStr);
                        plugin.getDataManager().setWhitelistEnabled(enabled);
                        sendSuccessResponse(ex, Map.of("success", true, "message", "白名单已" + (enabled ? "启用" : "禁用")));
                    } else {
                        sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "不支持的操作类型（仅add/toggle）");
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("❌ 处理白名单操作失败：" + e.getMessage());
                    sendErrorResponse(ex, StatusCodes.INTERNAL_SERVER_ERROR, "操作白名单失败：" + e.getMessage());
                }
            });
        }, (ex, exception) -> {
            // 错误回调也要调度到工作线程再发送响应
            ex.dispatch(() -> {
                plugin.getLogger().warning("⚠️ 接收白名单请求体失败：" + exception.getMessage());
                sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "无法读取请求体");
            });
        });
    }

    /**
     * 删除白名单
     */
    private void handleDeleteWhitelist(HttpServerExchange exchange) {
        var param = exchange.getQueryParameters().get("uuid");
        if (param == null || param.isEmpty()) {
            sendErrorResponse(exchange, StatusCodes.BAD_REQUEST, "玩家UUID不能为空");
            return;
        }
        String uuid = param.getFirst();
        if (uuid == null || uuid.trim().isEmpty()) {
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