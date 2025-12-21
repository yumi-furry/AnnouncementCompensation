package com.server.web.handler;

import com.server.AnnouncementCompensationPlugin;

import com.server.util.GsonUtils;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.bukkit.configuration.file.FileConfiguration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;


/**
 * 服务器配置API处理器
 * 处理服务器图标、MOTD等配置
 */
public class ServerHandler implements HttpHandler {
    private final AnnouncementCompensationPlugin plugin;


    public ServerHandler(AnnouncementCompensationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        // 获取请求路径
        String path = exchange.getRequestPath();

        // 根据不同的API路径处理不同的请求
        if (path.endsWith("/getConfig")) {
            handleGetConfig(exchange);

        } else if (path.endsWith("/updateIcon")) {
            handleUpdateIcon(exchange);
        } else if (path.endsWith("/getSmtpConfig")) {
            handleGetSmtpConfig(exchange);
        } else if (path.endsWith("/updateSmtpConfig")) {
            handleUpdateSmtpConfig(exchange);
        } else if (path.endsWith("/getQQApiConfig")) {
            handleGetQQApiConfig(exchange);
        } else if (path.endsWith("/updateQQApiConfig")) {
            handleUpdateQQApiConfig(exchange);
        } else {
            sendErrorResponse(exchange, StatusCodes.NOT_FOUND, "API路径不存在");
        }
    }

    // 处理获取服务器配置请求
    private void handleGetConfig(HttpServerExchange exchange) throws Exception {
        // 仅允许 GET
        if (!"GET".equals(exchange.getRequestMethod().toString())) {
            sendErrorResponse(exchange, StatusCodes.METHOD_NOT_ALLOWED, "仅支持GET请求");
            return;
        }

        // 获取参数
        String token = exchange.getQueryParameters().get("token") != null ? exchange.getQueryParameters().get("token").peekFirst() : null;

        if (token == null) {
            sendErrorResponse(exchange, StatusCodes.BAD_REQUEST, "Token不能为空");
            return;
        }

        // 验证Token
        LoginHandler loginHandler = plugin.getLoginHandler();
        if (loginHandler.validateToken(token) == null) {
            sendErrorResponse(exchange, StatusCodes.UNAUTHORIZED, "Token无效或已过期");
            return;
        }

        // 获取配置
        FileConfiguration config = plugin.getConfig();

        // 构建响应数据
        Map<String, Object> serverConfig = Map.of(
                "icon", Map.of(
                        "enable", config.getBoolean("server.icon.enable"),
                        "path", config.getString("server.icon.path"),
                        "resolution", config.getInt("server.icon.resolution")
                )
        );

        // 返回成功响应
        sendSuccessResponse(exchange, Map.of(
                "success", true,
                "message", "获取服务器配置成功",
                "config", serverConfig
        ));
        plugin.getLogger().info("管理员获取服务器配置成功");
    }



    // 处理更新图标配置请求
    private void handleUpdateIcon(HttpServerExchange exchange) throws Exception {
        // 仅允许 POST
        if (!"POST".equals(exchange.getRequestMethod().toString())) {
            sendErrorResponse(exchange, StatusCodes.METHOD_NOT_ALLOWED, "仅支持POST请求");
            return;
        }

        // 检查是否是文件上传请求
        String contentType = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
        if (contentType != null && contentType.startsWith("multipart/form-data")) {
            handleIconFileUpload(exchange);
        } else {
            // 异步接收完整请求体
            exchange.getRequestReceiver().receiveFullBytes((ex, bytes) -> {
                // 确保使用UTF-8编码解析请求体，避免中文乱码
                String message = new String(bytes, StandardCharsets.UTF_8);

                ex.dispatch(() -> {
                    try {
                        // 解析请求数据
                        Map<?, ?> requestData = GsonUtils.getGson().fromJson(message, Map.class);
                        String token = (String) requestData.get("token");

                        // 验证Token
                        LoginHandler loginHandler = plugin.getLoginHandler();
                        if (loginHandler.validateToken(token) == null) {
                            sendErrorResponse(ex, StatusCodes.UNAUTHORIZED, "Token无效或已过期");
                            return;
                        }

                        // 更新图标配置
                        FileConfiguration config = plugin.getConfig();
                        config.set("server.icon.enable", requestData.get("enable"));
                        config.set("server.icon.path", requestData.get("path"));
                        config.set("server.icon.resolution", requestData.get("resolution"));

                        // 保存配置
                        plugin.saveConfig();
                        plugin.getLogger().info("管理员更新了服务器图标配置");

                        // 返回成功响应
                        sendSuccessResponse(ex, Map.of(
                                "success", true,
                                "message", "更新服务器图标配置成功"
                        ));
                    } catch (Exception e) {
                        plugin.getLogger().severe("更新服务器图标配置失败：" + e.getMessage());
                        sendErrorResponse(ex, StatusCodes.INTERNAL_SERVER_ERROR, "更新服务器图标配置失败");
                    }
                });
            }, (ex, exception) -> {
                plugin.getLogger().warning("接收服务器图标更新请求体失败：" + exception.getMessage());
                sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "无法读取请求体");
            });
        }
    }

    // 处理图标文件上传
    private void handleIconFileUpload(HttpServerExchange exchange) throws Exception {
        // 获取请求头
        String token = exchange.getQueryParameters().get("token") != null ? exchange.getQueryParameters().get("token").peekFirst() : null;

        if (token == null) {
            sendErrorResponse(exchange, StatusCodes.BAD_REQUEST, "Token不能为空");
            return;
        }

        // 验证Token
        LoginHandler loginHandler = plugin.getLoginHandler();
        if (loginHandler.validateToken(token) == null) {
            sendErrorResponse(exchange, StatusCodes.UNAUTHORIZED, "Token无效或已过期");
            return;
        }

        // 解析multipart表单
        exchange.getRequestReceiver().receiveFullBytes((ex, bytes) -> {
            ex.dispatch(() -> {
                try {
                    // 创建临时文件保存图标
                    File tempFile = File.createTempFile("server-icon", ".png");
                    Files.write(tempFile.toPath(), bytes);

                    // 验证文件是否为PNG格式且分辨率正确
                    if (!isValidIconFile(tempFile)) {
                        tempFile.delete();
                        sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "图标文件必须是128x128的PNG格式");
                        return;
                    }

                    // 保存图标文件到服务器根目录
                    File serverIcon = new File(plugin.getDataFolder().getParentFile(), "server-icon.png");
                    Files.copy(tempFile.toPath(), serverIcon.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    tempFile.delete();

                    // 更新配置
                    FileConfiguration config = plugin.getConfig();
                    config.set("server.icon.enable", true);
                    config.set("server.icon.path", "server-icon.png");
                    config.set("server.icon.resolution", 128);
                    plugin.saveConfig();

                    // 返回成功响应
                    sendSuccessResponse(ex, Map.of(
                            "success", true,
                            "message", "图标文件上传成功",
                            "path", "server-icon.png"
                    ));
                    plugin.getLogger().info("管理员上传了新的服务器图标");
                } catch (Exception e) {
                    plugin.getLogger().severe("上传服务器图标失败：" + e.getMessage());
                    sendErrorResponse(ex, StatusCodes.INTERNAL_SERVER_ERROR, "上传服务器图标失败");
                }
            });
        }, (ex, exception) -> {
            plugin.getLogger().warning("接收服务器图标文件失败：" + exception.getMessage());
            sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "无法读取图标文件");
        });
    }

    // 验证图标文件是否有效
    private boolean isValidIconFile(File file) {
        try {
            // 检查文件扩展名
            if (!file.getName().endsWith(".png")) {
                return false;
            }

            // 检查文件大小（不超过1MB）
            if (file.length() > 1024 * 1024) {
                return false;
            }

            // 读取图像并检查分辨率
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                return false;
            }

            if (image.getWidth() != 128 || image.getHeight() != 128) {
                return false;
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("验证图标文件失败：" + e.getMessage());
            return false;
        }
    }

    // 处理获取SMTP配置请求
    private void handleGetSmtpConfig(HttpServerExchange exchange) throws Exception {
        // 仅允许 GET
        if (!"GET".equals(exchange.getRequestMethod().toString())) {
            sendErrorResponse(exchange, StatusCodes.METHOD_NOT_ALLOWED, "仅支持GET请求");
            return;
        }

        // 获取参数
        String token = exchange.getQueryParameters().get("token") != null ? exchange.getQueryParameters().get("token").peekFirst() : null;

        if (token == null) {
            sendErrorResponse(exchange, StatusCodes.BAD_REQUEST, "Token不能为空");
            return;
        }

        // 验证Token
        LoginHandler loginHandler = plugin.getLoginHandler();
        if (loginHandler.validateToken(token) == null) {
            sendErrorResponse(exchange, StatusCodes.UNAUTHORIZED, "Token无效或已过期");
            return;
        }

        // 获取配置
        FileConfiguration config = plugin.getConfig();

        // 构建响应数据
        Map<String, Object> smtpConfig = Map.of(
                "enable", config.getBoolean("smtp.enable", false),
                "host", config.getString("smtp.host", ""),
                "port", config.getInt("smtp.port", 587),
                "username", config.getString("smtp.username", ""),
                "password", config.getString("smtp.password", ""),
                "sender", config.getString("smtp.sender", ""),
                "protocol", config.getString("smtp.protocol", "smtp"),
                "ssl", config.getBoolean("smtp.ssl", false)
        );

        // 返回成功响应
        sendSuccessResponse(exchange, Map.of(
                "success", true,
                "message", "获取SMTP配置成功",
                "config", smtpConfig
        ));
        plugin.getLogger().info("管理员获取SMTP配置成功");
    }

    // 处理更新SMTP配置请求
    private void handleUpdateSmtpConfig(HttpServerExchange exchange) throws Exception {
        // 仅允许 POST
        if (!"POST".equals(exchange.getRequestMethod().toString())) {
            sendErrorResponse(exchange, StatusCodes.METHOD_NOT_ALLOWED, "仅支持POST请求");
            return;
        }

        // 异步接收完整请求体
        exchange.getRequestReceiver().receiveFullBytes((ex, bytes) -> {
            // 确保使用UTF-8编码解析请求体，避免中文乱码
            String message = new String(bytes, StandardCharsets.UTF_8);

            ex.dispatch(() -> {
                try {
                    // 解析请求数据
                    Map<?, ?> requestData = GsonUtils.getGson().fromJson(message, Map.class);
                    String token = (String) requestData.get("token");

                    // 验证Token
                    LoginHandler loginHandler = plugin.getLoginHandler();
                    if (loginHandler.validateToken(token) == null) {
                        sendErrorResponse(ex, StatusCodes.UNAUTHORIZED, "Token无效或已过期");
                        return;
                    }

                    // 更新SMTP配置
                    FileConfiguration config = plugin.getConfig();
                    config.set("smtp.enable", requestData.get("enable"));
                    config.set("smtp.host", requestData.get("host"));
                    config.set("smtp.port", requestData.get("port"));
                    config.set("smtp.username", requestData.get("username"));
                    config.set("smtp.password", requestData.get("password"));
                    config.set("smtp.sender", requestData.get("sender"));
                    config.set("smtp.protocol", requestData.get("protocol") != null ? requestData.get("protocol") : "smtp");
                    config.set("smtp.ssl", requestData.get("ssl") != null ? requestData.get("ssl") : false);

                    // 保存配置
                    plugin.saveConfig();
                    plugin.getLogger().info("管理员更新了SMTP配置");

                    // 返回成功响应
                    sendSuccessResponse(ex, Map.of(
                            "success", true,
                            "message", "更新SMTP配置成功"
                    ));
                } catch (Exception e) {
                    plugin.getLogger().severe("更新SMTP配置失败：" + e.getMessage());
                    sendErrorResponse(ex, StatusCodes.INTERNAL_SERVER_ERROR, "更新SMTP配置失败");
                }
            });
        }, (ex, exception) -> {
            plugin.getLogger().warning("接收SMTP更新请求体失败：" + exception.getMessage());
            sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "无法读取请求体");
        });
    }

    // 处理获取QQAPI配置请求
    private void handleGetQQApiConfig(HttpServerExchange exchange) throws Exception {
        // 仅允许 GET
        if (!"GET".equals(exchange.getRequestMethod().toString())) {
            sendErrorResponse(exchange, StatusCodes.METHOD_NOT_ALLOWED, "仅支持GET请求");
            return;
        }

        // 获取参数
        String token = exchange.getQueryParameters().get("token") != null ? exchange.getQueryParameters().get("token").peekFirst() : null;

        if (token == null) {
            sendErrorResponse(exchange, StatusCodes.BAD_REQUEST, "Token不能为空");
            return;
        }

        // 验证Token
        LoginHandler loginHandler = plugin.getLoginHandler();
        if (loginHandler.validateToken(token) == null) {
            sendErrorResponse(exchange, StatusCodes.UNAUTHORIZED, "Token无效或已过期");
            return;
        }

        // 获取配置
        FileConfiguration config = plugin.getConfig();

        // 构建响应数据
        Map<String, Object> qqApiConfig = Map.of(
                "enable", config.getBoolean("qqapi.enable", false),
                "appId", config.getString("qqapi.appId", ""),
                "appKey", config.getString("qqapi.appKey", ""),
                "redirectUri", config.getString("qqapi.redirectUri", ""),
                "scope", config.getString("qqapi.scope", "get_user_info")
        );

        // 返回成功响应
        sendSuccessResponse(exchange, Map.of(
                "success", true,
                "message", "获取QQAPI配置成功",
                "config", qqApiConfig
        ));
        plugin.getLogger().info("管理员获取QQAPI配置成功");
    }

    // 处理更新QQAPI配置请求
    private void handleUpdateQQApiConfig(HttpServerExchange exchange) throws Exception {
        // 仅允许 POST
        if (!"POST".equals(exchange.getRequestMethod().toString())) {
            sendErrorResponse(exchange, StatusCodes.METHOD_NOT_ALLOWED, "仅支持POST请求");
            return;
        }

        // 异步接收完整请求体
        exchange.getRequestReceiver().receiveFullBytes((ex, bytes) -> {
            // 确保使用UTF-8编码解析请求体，避免中文乱码
            String message = new String(bytes, StandardCharsets.UTF_8);

            ex.dispatch(() -> {
                try {
                    // 解析请求数据
                    Map<?, ?> requestData = GsonUtils.getGson().fromJson(message, Map.class);
                    String token = (String) requestData.get("token");

                    // 验证Token
                    LoginHandler loginHandler = plugin.getLoginHandler();
                    if (loginHandler.validateToken(token) == null) {
                        sendErrorResponse(ex, StatusCodes.UNAUTHORIZED, "Token无效或已过期");
                        return;
                    }

                    // 更新QQAPI配置
                    FileConfiguration config = plugin.getConfig();
                    config.set("qqapi.enable", requestData.get("enable"));
                    config.set("qqapi.appId", requestData.get("appId"));
                    config.set("qqapi.appKey", requestData.get("appKey"));
                    config.set("qqapi.redirectUri", requestData.get("redirectUri"));
                    config.set("qqapi.scope", requestData.get("scope") != null ? requestData.get("scope") : "get_user_info");

                    // 保存配置
                    plugin.saveConfig();
                    plugin.getLogger().info("管理员更新了QQAPI配置");

                    // 返回成功响应
                    sendSuccessResponse(ex, Map.of(
                            "success", true,
                            "message", "更新QQAPI配置成功"
                    ));
                } catch (Exception e) {
                    plugin.getLogger().severe("更新QQAPI配置失败：" + e.getMessage());
                    sendErrorResponse(ex, StatusCodes.INTERNAL_SERVER_ERROR, "更新QQAPI配置失败");
                }
            });
        }, (ex, exception) -> {
            plugin.getLogger().warning("接收QQAPI更新请求体失败：" + exception.getMessage());
            sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "无法读取请求体");
        });
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