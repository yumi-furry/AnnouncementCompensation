package com.server.web.handler;

import com.server.AnnouncementCompensationPlugin;
import com.server.data.model.EmailVerificationCode;
import com.server.data.model.QQBinding;
import com.server.data.model.User;
import com.server.util.GsonUtils;
import com.server.util.MailUtils;
import com.server.web.WebPermission;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户API处理器（注册、登录、邮箱验证等）
 */
public class UserHandler implements HttpHandler {
    private final AnnouncementCompensationPlugin plugin;
    // 线程安全的 Token 存储
    private final Map<String, User> tokenMap = new ConcurrentHashMap<>();

    public UserHandler(AnnouncementCompensationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        // 获取请求路径
        String path = exchange.getRequestPath();

        // 验证管理员权限的API（需要管理员登录）
        if (path.endsWith("/validateToken") || path.endsWith("/sendVerificationCode") || 
            path.endsWith("/verifyEmail") || path.endsWith("/bindGameRole") || 
            path.endsWith("/bindQQ") || path.endsWith("/loginQQ")) {
            
            // 验证管理员身份和权限
            if (!isAdminAuthenticated(exchange)) {
                return;
            }
        }

        // 根据不同的API路径处理不同的请求
        if (path.endsWith("/register")) {
            handleRegister(exchange);
        } else if (path.endsWith("/login")) {
            handleUserLogin(exchange);
        } else if (path.endsWith("/sendVerificationCode")) {
            handleSendVerificationCode(exchange);
        } else if (path.endsWith("/verifyEmail")) {
            handleVerifyEmail(exchange);
        } else if (path.endsWith("/bindGameRole")) {
            handleBindGameRole(exchange);
        } else if (path.endsWith("/bind-role")) {
            handleBindRole(exchange);
        } else if (path.endsWith("/validateToken")) {
            handleValidateToken(exchange);
        } else if (path.endsWith("/bindQQ")) {
            handleBindQQ(exchange);
        } else if (path.endsWith("/loginQQ")) {
            handleQQLogin(exchange);
        } else {
            sendErrorResponse(exchange, StatusCodes.NOT_FOUND, "API路径不存在");
        }
    }

    // 处理用户注册请求
    private void handleRegister(HttpServerExchange exchange) throws Exception {
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
                    // 解析注册请求
                    RegisterRequest request = GsonUtils.getGson().fromJson(message, RegisterRequest.class);
                    if (request == null || request.getUsername() == null || request.getPassword() == null || request.getEmail() == null) {
                        sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "用户名、密码和邮箱不能为空");
                        return;
                    }

                    // 检查用户名是否已存在
                    if (plugin.getDataManager().getUserByUsername(request.getUsername()) != null) {
                        sendErrorResponse(ex, StatusCodes.CONFLICT, "用户名已存在");
                        return;
                    }

                    // 检查邮箱是否已存在
                    if (plugin.getDataManager().getUserByEmail(request.getEmail()) != null) {
                        sendErrorResponse(ex, StatusCodes.CONFLICT, "邮箱已被注册");
                        return;
                    }

                    // 创建新用户
                    User user = new User(request.getUsername(), request.getPassword(), request.getEmail());
                    plugin.getDataManager().saveUser(user);

                    // 发送邮箱验证码
                    if (!sendVerificationCode(user.getEmail(), EmailVerificationCode.CodeType.REGISTER)) {
                        sendErrorResponse(ex, StatusCodes.INTERNAL_SERVER_ERROR, "验证码发送失败，请稍后重试");
                        return;
                    }

                    // 返回成功响应
                    Map<String, Object> response = Map.of(
                            "success", true,
                            "message", "注册成功，请检查邮箱获取验证码",
                            "username", user.getUsername(),
                            "email", user.getEmail()
                    );
                    sendSuccessResponse(ex, response);
                    plugin.getLogger().info("用户 " + user.getUsername() + " 注册成功");
                } catch (Exception e) {
                    plugin.getLogger().severe("❌ 用户注册失败：" + e.getMessage());
                    sendErrorResponse(ex, StatusCodes.INTERNAL_SERVER_ERROR, "注册失败，请稍后重试");
                }
            });
        }, (ex, exception) -> {
            plugin.getLogger().warning("⚠️ 接收注册请求体失败：" + exception.getMessage());
            sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "无法读取请求体");
        });
    }

    // 处理用户登录请求
    private void handleUserLogin(HttpServerExchange exchange) throws Exception {
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
                    // 解析登录请求
                    LoginRequest request = GsonUtils.getGson().fromJson(message, LoginRequest.class);
                    if (request == null || request.getPassword() == null) {
                        sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "密码不能为空");
                        return;
                    }

                    User user = null;

                    // 支持两种登录方式：1. 用户名+密码  2. 仅密码（已绑定角色的用户）
                    if (request.getUsername() != null) {
                        // 传统登录方式：用户名+密码
                        user = plugin.getDataManager().getUserByUsername(request.getUsername());
                        if (user == null) {
                            sendErrorResponse(ex, StatusCodes.UNAUTHORIZED, "用户名不存在");
                            return;
                        }
                    } else {
                        // 新登录方式：仅密码（查找已绑定角色的用户）
                        // 注意：这里需要根据实际情况调整，可能需要从请求中获取更多信息
                        // 为了演示，我们假设只有一个已绑定角色的用户
                        for (User u : plugin.getDataManager().getUsers()) {
                            if (u.isGameRoleBound() && u.verifyPassword(request.getPassword())) {
                                user = u;
                                break;
                            }
                        }

                        if (user == null) {
                            sendErrorResponse(ex, StatusCodes.UNAUTHORIZED, "密码错误或未绑定角色");
                            return;
                        }
                    }

                    // 验证密码
                    if (!user.verifyPassword(request.getPassword())) {
                        sendErrorResponse(ex, StatusCodes.UNAUTHORIZED, "密码错误");
                        return;
                    }

                    // 检查邮箱是否已验证
                    if (!user.isVerified()) {
                        sendErrorResponse(ex, StatusCodes.FORBIDDEN, "邮箱未验证，请先验证邮箱");
                        return;
                    }

                    // 更新最后登录时间
                    user.updateLastLoginTime();
                    plugin.getDataManager().saveUser(user);

                    // 生成登录Token
                    String token = UUID.randomUUID().toString().replace("-", "");
                    tokenMap.put(token, user);

                    // 返回成功响应
                    Map<String, Object> userInfo = Map.of(
                            "username", user.getUsername(),
                            "email", user.getEmail(),
                            "isGameRoleBound", user.isGameRoleBound(),
                            "permissions", user.getPermissions()
                    );
                    
                    Map<String, Object> response = Map.of(
                            "success", true,
                            "message", "登录成功",
                            "token", token,
                            "user", userInfo
                    );
                    sendSuccessResponse(ex, response);
                    plugin.getLogger().info("用户 " + user.getUsername() + " 登录Web面板");
                } catch (Exception e) {
                    plugin.getLogger().severe("❌ 用户登录失败：" + e.getMessage());
                    sendErrorResponse(ex, StatusCodes.INTERNAL_SERVER_ERROR, "登录失败，请稍后重试");
                }
            });
        }, (ex, exception) -> {
            plugin.getLogger().warning("⚠️ 接收登录请求体失败：" + exception.getMessage());
            sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "无法读取请求体");
        });
    }

    // 处理发送验证码请求
    private void handleSendVerificationCode(HttpServerExchange exchange) throws Exception {
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
                    // 解析请求
                    VerificationCodeRequest request = GsonUtils.getGson().fromJson(message, VerificationCodeRequest.class);
                    if (request == null || request.getEmail() == null || request.getType() == null) {
                        sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "邮箱和验证码类型不能为空");
                        return;
                    }

                    // 验证邮箱是否已被注册（注册类型时）
                    if (request.getType().equals("REGISTER")) {
                        if (plugin.getDataManager().getUserByEmail(request.getEmail()) != null) {
                            sendErrorResponse(ex, StatusCodes.CONFLICT, "该邮箱已被注册");
                            return;
                        }
                    }

                    // 验证邮箱是否存在（非注册类型时）
                    if (!request.getType().equals("REGISTER")) {
                        if (plugin.getDataManager().getUserByEmail(request.getEmail()) == null) {
                            sendErrorResponse(ex, StatusCodes.NOT_FOUND, "该邮箱未注册");
                            return;
                        }
                    }

                    // 发送验证码
                    EmailVerificationCode.CodeType codeType;
                    try {
                        codeType = EmailVerificationCode.CodeType.valueOf(request.getType());
                    } catch (IllegalArgumentException e) {
                        sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "无效的验证码类型");
                        return;
                    }

                    if (!sendVerificationCode(request.getEmail(), codeType)) {
                        sendErrorResponse(ex, StatusCodes.INTERNAL_SERVER_ERROR, "验证码发送失败，请稍后重试");
                        return;
                    }

                    // 返回成功响应
                    Map<String, Object> response = Map.of(
                            "success", true,
                            "message", "验证码发送成功，请检查邮箱"
                    );
                    sendSuccessResponse(ex, response);
                } catch (Exception e) {
                    plugin.getLogger().severe("❌ 发送验证码失败：" + e.getMessage());
                    sendErrorResponse(ex, StatusCodes.INTERNAL_SERVER_ERROR, "发送验证码失败，请稍后重试");
                }
            });
        }, (ex, exception) -> {
            plugin.getLogger().warning("⚠️ 接收发送验证码请求体失败：" + exception.getMessage());
            sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "无法读取请求体");
        });
    }

    // 处理邮箱验证请求
    private void handleVerifyEmail(HttpServerExchange exchange) throws Exception {
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
                    // 解析请求
                    VerifyEmailRequest request = GsonUtils.getGson().fromJson(message, VerifyEmailRequest.class);
                    if (request == null || request.getEmail() == null || request.getCode() == null) {
                        sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "邮箱和验证码不能为空");
                        return;
                    }

                    // 获取验证码
                    EmailVerificationCode code = plugin.getDataManager().getEmailVerificationCode(request.getEmail(), EmailVerificationCode.CodeType.REGISTER);
                    if (code == null) {
                        sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "验证码不存在或已过期");
                        return;
                    }

                    // 验证验证码
                    if (!code.isCodeValid(request.getCode())) {
                        sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "验证码错误或已过期");
                        return;
                    }

                    // 更新用户状态
                    User user = plugin.getDataManager().getUserByEmail(request.getEmail());
                    if (user == null) {
                        sendErrorResponse(ex, StatusCodes.NOT_FOUND, "用户不存在");
                        return;
                    }

                    user.setVerified(true);
                    plugin.getDataManager().saveUser(user);

                    // 删除已使用的验证码
                    plugin.getDataManager().deleteEmailVerificationCode(code.getIdString());

                    // 返回成功响应
                    Map<String, Object> response = Map.of(
                            "success", true,
                            "message", "邮箱验证成功"
                    );
                    sendSuccessResponse(ex, response);
                    plugin.getLogger().info("用户 " + user.getUsername() + " 邮箱验证成功");
                } catch (Exception e) {
                    plugin.getLogger().severe("❌ 邮箱验证失败：" + e.getMessage());
                    sendErrorResponse(ex, StatusCodes.INTERNAL_SERVER_ERROR, "邮箱验证失败，请稍后重试");
                }
            });
        }, (ex, exception) -> {
            plugin.getLogger().warning("⚠️ 接收邮箱验证请求体失败：" + exception.getMessage());
            sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "无法读取请求体");
        });
    }

    // 处理游戏角色绑定请求
    private void handleBindGameRole(HttpServerExchange exchange) throws Exception {
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
                    // 解析请求
                    BindGameRoleRequest request = GsonUtils.getGson().fromJson(message, BindGameRoleRequest.class);
                    if (request == null || request.getUsername() == null || request.getVerificationKey() == null) {
                        sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "用户名和验证密钥不能为空");
                        return;
                    }

                    // 验证用户
                    User user = plugin.getDataManager().getUserByUsername(request.getUsername());
                    if (user == null) {
                        sendErrorResponse(ex, StatusCodes.NOT_FOUND, "用户不存在");
                        return;
                    }

                    // 验证邮箱
                    if (!user.isVerified()) {
                        sendErrorResponse(ex, StatusCodes.FORBIDDEN, "邮箱未验证，请先验证邮箱");
                        return;
                    }

                    // 验证密钥
                    if (!user.getVerificationKey().equals(request.getVerificationKey())) {
                        sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "验证密钥错误");
                        return;
                    }

                    // 绑定游戏角色
                    user.setGameRoleBound(true);
                    // 注意：这里的gameUUID需要从游戏内获取，暂时设为null，后续在游戏内完成绑定
                    plugin.getDataManager().saveUser(user);

                    // 返回成功响应
                    Map<String, Object> response = Map.of(
                            "success", true,
                            "message", "游戏角色绑定成功"
                    );
                    sendSuccessResponse(ex, response);
                    plugin.getLogger().info("用户 " + user.getUsername() + " 游戏角色绑定成功");
                } catch (Exception e) {
                    plugin.getLogger().severe("❌ 游戏角色绑定失败：" + e.getMessage());
                    sendErrorResponse(ex, StatusCodes.INTERNAL_SERVER_ERROR, "游戏角色绑定失败，请稍后重试");
                }
            });
        }, (ex, exception) -> {
            plugin.getLogger().warning("⚠️ 接收游戏角色绑定请求体失败：" + exception.getMessage());
            sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "无法读取请求体");
        });
    }

    // 处理Token验证请求
    private void handleValidateToken(HttpServerExchange exchange) throws Exception {
        // 仅允许 GET
        if (!"GET".equals(exchange.getRequestMethod().toString())) {
            sendErrorResponse(exchange, StatusCodes.METHOD_NOT_ALLOWED, "仅支持GET请求");
            return;
        }

        // 获取Token参数
        String token = exchange.getQueryParameters().get("token") != null ? exchange.getQueryParameters().get("token").peekFirst() : null;
        if (token == null) {
            sendErrorResponse(exchange, StatusCodes.BAD_REQUEST, "Token不能为空");
            return;
        }

        // 验证Token
        User user = validateToken(token);
        if (user == null) {
            sendErrorResponse(exchange, StatusCodes.UNAUTHORIZED, "Token无效或已过期");
            return;
        }

        // 返回成功响应
        Map<String, Object> response = Map.of(
                "success", true,
                "message", "Token有效",
                "username", user.getUsername(),
                "email", user.getEmail(),
                "isGameRoleBound", user.isGameRoleBound()
        );
        sendSuccessResponse(exchange, response);
    }

    // 发送验证码
    private boolean sendVerificationCode(String email, EmailVerificationCode.CodeType type) {
        try {
            // 创建验证码
            EmailVerificationCode code = new EmailVerificationCode(email, type);
            
            // 保存验证码
            plugin.getDataManager().saveEmailVerificationCode(code);

            // 发送邮件
            String subject = "";
            String content = "";
            
            switch (type) {
                case REGISTER -> {
                    subject = "注册验证"; 
                    content = "您的注册验证码是：" + code.getCode() + "，有效期5分钟。";
                }
                case RESET_PASSWORD -> {
                    subject = "重置密码验证"; 
                    content = "您的重置密码验证码是：" + code.getCode() + "，有效期5分钟。";
                }
                case CHANGE_EMAIL -> {
                    subject = "修改邮箱验证"; 
                    content = "您的修改邮箱验证码是：" + code.getCode() + "，有效期5分钟。";
                }
            }

            // 使用MailUtils发送邮件
            return MailUtils.sendMail(plugin.getConfig(), email, subject, content);
        } catch (Exception e) {
            plugin.getLogger().severe("❌ 发送验证码失败：" + e.getMessage());
            return false;
        }
    }

    /**
     * 验证Token是否有效（供其他Handler调用）
     */
    public User validateToken(String token) {
        return tokenMap.get(token);
    }

    /**
     * 退出登录（移除Token）
     */
    public void logout(String token) {
        tokenMap.remove(token);
    }

    // 处理QQ绑定请求
    private void handleBindQQ(HttpServerExchange exchange) throws Exception {
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
                    // 解析请求
                    BindQQRequest request = GsonUtils.getGson().fromJson(message, BindQQRequest.class);
                    if (request == null || request.getToken() == null || request.getQqOpenId() == null || request.getQqUnionId() == null) {
                        sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "Token、QQ OpenId和QQ UnionId不能为空");
                        return;
                    }

                    // 验证Token
                    User user = validateToken(request.getToken());
                    if (user == null) {
                        sendErrorResponse(ex, StatusCodes.UNAUTHORIZED, "Token无效或已过期");
                        return;
                    }

                    // 检查邮箱是否已验证
                    if (!user.isVerified()) {
                        sendErrorResponse(ex, StatusCodes.FORBIDDEN, "邮箱未验证，请先验证邮箱");
                        return;
                    }

                    // 检查QQ是否已经被绑定
                    if (plugin.getDataManager().getUserByQQOpenId(request.getQqOpenId()) != null) {
                        sendErrorResponse(ex, StatusCodes.CONFLICT, "该QQ已被绑定到其他账户");
                        return;
                    }

                    if (plugin.getDataManager().getUserByQQUnionId(request.getQqUnionId()) != null) {
                        sendErrorResponse(ex, StatusCodes.CONFLICT, "该QQ已被绑定到其他账户");
                        return;
                    }

                    // 创建QQ绑定信息
                    QQBinding qqBinding = new QQBinding(
                            request.getQqOpenId(),
                            request.getQqUnionId(),
                            request.getNickname(),
                            request.getAvatarUrl()
                    );

                    // 绑定QQ到用户
                    user.setQqBinding(qqBinding);
                    plugin.getDataManager().saveUser(user);

                    // 返回成功响应
                    Map<String, Object> response = Map.of(
                            "success", true,
                            "message", "QQ绑定成功",
                            "username", user.getUsername(),
                            "qqNickname", qqBinding.getNickname()
                    );
                    sendSuccessResponse(ex, response);
                    plugin.getLogger().info("用户 " + user.getUsername() + " QQ绑定成功");
                } catch (Exception e) {
                    plugin.getLogger().severe("❌ QQ绑定失败：" + e.getMessage());
                    sendErrorResponse(ex, StatusCodes.INTERNAL_SERVER_ERROR, "QQ绑定失败，请稍后重试");
                }
            });
        }, (ex, exception) -> {
            plugin.getLogger().warning("⚠️ 接收QQ绑定请求体失败：" + exception.getMessage());
            sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "无法读取请求体");
        });
    }

    // 处理QQ登录请求
    private void handleQQLogin(HttpServerExchange exchange) throws Exception {
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
                    // 解析请求
                    QQLoginRequest request = GsonUtils.getGson().fromJson(message, QQLoginRequest.class);
                    if (request == null || request.getQqOpenId() == null || request.getQqUnionId() == null) {
                        sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "QQ OpenId和QQ UnionId不能为空");
                        return;
                    }

                    // 检查QQ是否已经被绑定
                    User user = plugin.getDataManager().getUserByQQOpenId(request.getQqOpenId());
                    if (user == null) {
                        user = plugin.getDataManager().getUserByQQUnionId(request.getQqUnionId());
                        if (user == null) {
                            sendErrorResponse(ex, StatusCodes.NOT_FOUND, "该QQ尚未绑定任何账户");
                            return;
                        }
                    }

                    // 检查邮箱是否已验证
                    if (!user.isVerified()) {
                        sendErrorResponse(ex, StatusCodes.FORBIDDEN, "邮箱未验证，请先验证邮箱");
                        return;
                    }

                    // 更新最后登录时间
                    user.updateLastLoginTime();
                    plugin.getDataManager().saveUser(user);

                    // 生成登录Token
                    String token = UUID.randomUUID().toString().replace("-", "");
                    tokenMap.put(token, user);

                    // 返回成功响应
                    Map<String, Object> response = Map.of(
                            "success", true,
                            "message", "QQ登录成功",
                            "token", token,
                            "username", user.getUsername(),
                            "email", user.getEmail(),
                            "isGameRoleBound", user.isGameRoleBound(),
                            "permissions", user.getPermissions()
                    );
                    sendSuccessResponse(ex, response);
                    plugin.getLogger().info("用户 " + user.getUsername() + " QQ登录成功");
                } catch (Exception e) {
                    plugin.getLogger().severe("❌ QQ登录失败：" + e.getMessage());
                    sendErrorResponse(ex, StatusCodes.INTERNAL_SERVER_ERROR, "QQ登录失败，请稍后重试");
                }
            });
        }, (ex, exception) -> {
            plugin.getLogger().warning("⚠️ 接收QQ登录请求体失败：" + exception.getMessage());
            sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "无法读取请求体");
        });
    }

    // 处理角色绑定请求（通过绝对ID）
    private void handleBindRole(HttpServerExchange exchange) throws Exception {
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
                    // 解析请求
                    BindRoleRequest request = GsonUtils.getGson().fromJson(message, BindRoleRequest.class);
                    if (request == null || request.getAbsoluteId() == null) {
                        sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "绝对ID不能为空");
                        return;
                    }

                    // 从绝对ID中提取玩家UUID（假设绝对ID格式为UUID-时间戳）
                    String[] idParts = request.getAbsoluteId().split("-");
                    if (idParts.length < 2) {
                        sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "无效的绝对ID格式");
                        return;
                    }

                    // 重建UUID部分（去掉时间戳）
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < idParts.length - 1; i++) {
                        if (i > 0) sb.append("-");
                        sb.append(idParts[i]);
                    }
                    String playerUUID = sb.toString();

                    // 验证玩家UUID是否存在（这里可以根据实际情况添加更严格的验证）
                    if (playerUUID == null || playerUUID.length() < 36) {
                        sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "无效的玩家UUID");
                        return;
                    }

                    // 查找未绑定角色的用户（这里简单处理，实际应该根据注册流程获取对应的用户）
                    // 注意：这里需要根据实际情况调整，可能需要从注册流程中获取用户信息
                    // 为了演示，我们假设最新注册的未绑定用户就是要绑定的用户
                    User userToBind = null;
                    for (User user : plugin.getDataManager().getUsers()) {
                        if (!user.isGameRoleBound()) {
                            userToBind = user;
                            break;
                        }
                    }

                    if (userToBind == null) {
                        sendErrorResponse(ex, StatusCodes.NOT_FOUND, "没有找到可绑定的用户，请先完成注册");
                        return;
                    }

                    // 绑定游戏角色
                    userToBind.setGameUUID(playerUUID);
                    userToBind.setGameRoleBound(true);
                    plugin.getDataManager().saveUser(userToBind);

                    // 返回成功响应
                    Map<String, Object> response = Map.of(
                            "success", true,
                            "message", "角色绑定成功",
                            "username", userToBind.getUsername(),
                            "email", userToBind.getEmail()
                    );
                    sendSuccessResponse(ex, response);
                    plugin.getLogger().info("用户 " + userToBind.getUsername() + " 绑定游戏角色成功，玩家UUID：" + playerUUID);
                } catch (Exception e) {
                    plugin.getLogger().severe("❌ 角色绑定失败：" + e.getMessage());
                    sendErrorResponse(ex, StatusCodes.INTERNAL_SERVER_ERROR, "角色绑定失败，请稍后重试");
                }
            });
        }, (ex, exception) -> {
            plugin.getLogger().warning("⚠️ 接收角色绑定请求体失败：" + exception.getMessage());
            sendErrorResponse(ex, StatusCodes.BAD_REQUEST, "无法读取请求体");
        });
    }

    // ====================== 请求模型类 ======================
    private static class RegisterRequest {
        private String username;
        private String password;
        private String email;

        // Getters and setters
        public String getUsername() { return username; }
        @SuppressWarnings("unused")
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        @SuppressWarnings("unused")
        public void setPassword(String password) { this.password = password; }
        public String getEmail() { return email; }
        @SuppressWarnings("unused")
        public void setEmail(String email) { this.email = email; }
    }

    private static class LoginRequest {
        private String username;
        private String password;

        // Getters and setters
        public String getUsername() { return username; }
        @SuppressWarnings("unused")
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        @SuppressWarnings("unused")
        public void setPassword(String password) { this.password = password; }
    }

    private static class VerificationCodeRequest {
        private String email;
        private String type;

        // Getters and setters
        public String getEmail() { return email; }
        @SuppressWarnings("unused")
        public void setEmail(String email) { this.email = email; }
        public String getType() { return type; }
        @SuppressWarnings("unused")
        public void setType(String type) { this.type = type; }
    }

    private static class VerifyEmailRequest {
        private String email;
        private String code;

        // Getters and setters
        public String getEmail() { return email; }
        @SuppressWarnings("unused")
        public void setEmail(String email) { this.email = email; }
        public String getCode() { return code; }
        @SuppressWarnings("unused")
        public void setCode(String code) { this.code = code; }
    }

    private static class BindGameRoleRequest {
        private String username;
        private String verificationKey;

        // Getters and setters
        public String getUsername() { return username; }
        @SuppressWarnings("unused")
        public void setUsername(String username) { this.username = username; }
        public String getVerificationKey() { return verificationKey; }
        @SuppressWarnings("unused")
        public void setVerificationKey(String verificationKey) { this.verificationKey = verificationKey; }
    }

    private static class BindQQRequest {
        private String token;
        private String qqOpenId;
        private String qqUnionId;
        private String nickname;
        private String avatarUrl;

        // Getters and setters
        public String getToken() { return token; }
        @SuppressWarnings("unused")
        public void setToken(String token) { this.token = token; }
        public String getQqOpenId() { return qqOpenId; }
        @SuppressWarnings("unused")
        public void setQqOpenId(String qqOpenId) { this.qqOpenId = qqOpenId; }
        public String getQqUnionId() { return qqUnionId; }
        @SuppressWarnings("unused")
        public void setQqUnionId(String qqUnionId) { this.qqUnionId = qqUnionId; }
        public String getNickname() { return nickname; }
        @SuppressWarnings("unused")
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getAvatarUrl() { return avatarUrl; }
        @SuppressWarnings("unused")
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    }

    private static class QQLoginRequest {
        private String qqOpenId;
        private String qqUnionId;

        // Getters and setters
        public String getQqOpenId() { return qqOpenId; }
        @SuppressWarnings("unused")
        public void setQqOpenId(String qqOpenId) { this.qqOpenId = qqOpenId; }
        public String getQqUnionId() { return qqUnionId; }
        @SuppressWarnings("unused")
        public void setQqUnionId(String qqUnionId) { this.qqUnionId = qqUnionId; }
    }

    private static class BindRoleRequest {
        private String absoluteId;

        // Getters and setters
        public String getAbsoluteId() { return absoluteId; }
        @SuppressWarnings("unused")
        public void setAbsoluteId(String absoluteId) { this.absoluteId = absoluteId; }
    }

    /**
     * 验证管理员身份和权限
     * @param exchange HTTP请求交换对象
     * @return true=验证通过，false=验证失败（已发送错误响应）
     */
    private boolean isAdminAuthenticated(HttpServerExchange exchange) {
        try {
            // 从请求头获取Token
            String token = exchange.getRequestHeaders().getFirst("Authorization");
            if (token == null || token.isEmpty()) {
                sendErrorResponse(exchange, StatusCodes.UNAUTHORIZED, "缺少管理员身份验证Token");
                return false;
            }

            // 移除Bearer前缀（如果存在）
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            // 验证管理员Token
            if (!plugin.getLoginHandler().isValidToken(token)) {
                sendErrorResponse(exchange, StatusCodes.UNAUTHORIZED, "管理员身份验证失败");
                return false;
            }

            // 获取管理员信息
            if (plugin.getLoginHandler().isAdminToken(token)) {
                // 检查是否具有用户管理权限
                if (!WebPermission.ALL.hasPermission(plugin.getLoginHandler().getAdminPermissions(token))) {
                    sendErrorResponse(exchange, StatusCodes.FORBIDDEN, "没有用户管理权限");
                    return false;
                }
            } else {
                sendErrorResponse(exchange, StatusCodes.FORBIDDEN, "需要管理员权限");
                return false;
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("❌ 管理员身份验证异常：" + e.getMessage());
            sendErrorResponse(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "身份验证异常");
            return false;
        }
    }

    // ====================== 响应工具方法 ======================
    private void sendSuccessResponse(HttpServerExchange exchange, Object data) {
        Map<String, Object> response = Map.of(
                "success", true,
                "data", data
        );

        exchange.setStatusCode(200);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json;charset=UTF-8");
        exchange.getResponseSender().send(GsonUtils.getGson().toJson(response));
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