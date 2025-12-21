package com.server.web.handler;

import com.server.AnnouncementCompensationPlugin;
import com.server.data.model.Announcement;
import com.server.web.WebPermission;
import com.server.util.GsonUtils;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 公告管理API处理器
 * 处理 /api/announcement GET/POST请求，增删改查公告
 */
public class AnnouncementHandler implements HttpHandler {
    private final AnnouncementCompensationPlugin plugin;
    private final LoginHandler loginHandler;

    public AnnouncementHandler(AnnouncementCompensationPlugin plugin) {
        this.plugin = plugin;
        // 从插件主类获取 LoginHandler 实例
        this.loginHandler = plugin.getLoginHandler();
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        // 1. 验证登录Token
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

        // 2. 校验公告管理权限
        if (!WebPermission.ANNOUNCEMENT.hasPermission(admin.getPermissions())) {
            sendErrorResponse(exchange, StatusCodes.FORBIDDEN, "无公告管理权限");
            return;
        }

        // 3. 处理不同请求方法
        String method = exchange.getRequestMethod().toString();
        switch (method) {
            case "GET":
                handleGetAnnouncements(exchange);
                break;
            case "POST":
                handleAddAnnouncement(exchange);
                break;
            case "DELETE":
                handleDeleteAnnouncement(exchange);
                break;
            default:
                sendErrorResponse(exchange, StatusCodes.METHOD_NOT_ALLOWED, "仅支持GET/POST/DELETE请求");
        }
    }

    /**
     * 获取所有公告
     */
    private void handleGetAnnouncements(HttpServerExchange exchange) {
        List<Announcement> announcements = plugin.getDataManager().getAllAnnouncements();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", announcements);
        sendSuccessResponse(exchange, response);
    }

    /**
     * 添加新公告（改为异步接收请求体并在工作线程处理）
     */
    private void handleAddAnnouncement(HttpServerExchange exchange) {
        exchange.getRequestReceiver().receiveFullBytes((ex, bytes) -> {
            ex.dispatch(() -> {
                try {
                    if (bytes == null || bytes.length == 0) {
                        sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "请求体为空");
                        return;
                    }
                    // 使用UTF-8编码解析请求体
                    String message = new String(bytes, StandardCharsets.UTF_8);
                    Announcement announcement;
                    try {
                        announcement = GsonUtils.getGson().fromJson(message, Announcement.class);
                    } catch (Exception je) {
                        plugin.getLogger().warning("⚠️ 解析公告请求JSON失败：" + je.getMessage());
                        sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "请求体格式不正确（非JSON）");
                        return;
                    }

                    if (announcement == null || announcement.getName() == null || announcement.getContent() == null) {
                        sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "公告名称/内容不能为空");
                        return;
                    }

                    plugin.getDataManager().saveAnnouncement(announcement);
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("message", "公告添加成功");
                    response.put("data", announcement);
                    sendSuccessResponse(ex, response);
                } catch (Exception e) {
                    plugin.getLogger().severe("❌ 添加公告失败：" + e.getMessage());
                    sendErrorResponse(ex, StatusCodes.INTERNAL_SERVER_ERROR, "添加公告失败：" + e.getMessage());
                }
            });
        }, (ex, exception) -> {
            ex.dispatch(() -> {
                plugin.getLogger().warning("⚠️ 接收公告请求体失败：" + exception.getMessage());
                sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "无法读取请求体");
            });
        });
    }

    /**
     * 删除公告
     */
    private void handleDeleteAnnouncement(HttpServerExchange exchange) {
        var param = exchange.getQueryParameters().get("id");
        if (param == null || param.isEmpty()) {
            sendErrorResponse(exchange, StatusCodes.BAD_REQUEST, "公告ID不能为空");
            return;
        }
        String id = param.getFirst();
        if (id == null || id.trim().isEmpty()) {
            sendErrorResponse(exchange, StatusCodes.BAD_REQUEST, "公告ID不能为空");
            return;
        }

        boolean deleted = plugin.getDataManager().deleteAnnouncement(id);
        if (deleted) {
            sendSuccessResponse(exchange, Map.of("success", true, "message", "公告删除成功"));
        } else {
            sendErrorResponse(exchange, StatusCodes.NOT_FOUND, "公告不存在");
        }
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