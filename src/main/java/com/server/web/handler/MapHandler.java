package com.server.web.handler;

import com.server.AnnouncementCompensationPlugin;
import com.server.util.GsonUtils;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * 地图查看API处理器
 */
public class MapHandler implements HttpHandler {
    private final AnnouncementCompensationPlugin plugin;


    public MapHandler(AnnouncementCompensationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        // 获取请求路径
        String path = exchange.getRequestPath();

        // 根据不同的API路径处理不同的请求
        if (path.endsWith("/getPlayerMap")) {
            handleGetPlayerMap(exchange);
        } else if (path.endsWith("/getOnlinePlayers")) {
            handleGetOnlinePlayers(exchange);
        } else {
            sendErrorResponse(exchange, StatusCodes.NOT_FOUND, "API路径不存在");
        }
    }

    // 处理获取在线玩家列表请求
    private void handleGetOnlinePlayers(HttpServerExchange exchange) throws Exception {
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
        UserHandler userHandler = plugin.getUserHandler();
        if (userHandler.validateToken(token) == null) {
            sendErrorResponse(exchange, StatusCodes.UNAUTHORIZED, "Token无效或已过期");
            return;
        }

        // 获取所有在线玩家信息
        List<Map<String, Object>> onlinePlayers = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location location = player.getLocation();
            Map<String, Object> playerInfo = new java.util.HashMap<>();
        playerInfo.put("name", player.getName());
        playerInfo.put("displayName", player.displayName());
        playerInfo.put("world", location.getWorld().getName());
        playerInfo.put("x", location.getX());
        playerInfo.put("y", location.getY());
        playerInfo.put("z", location.getZ());
        playerInfo.put("health", player.getHealth());
        playerInfo.put("maxHealth", player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue());
        playerInfo.put("foodLevel", player.getFoodLevel());
        playerInfo.put("gamemode", player.getGameMode().name());
        playerInfo.put("onlineTime", player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) * 60 * 1000L);
            onlinePlayers.add(playerInfo);
        }

        // 返回成功响应
        sendSuccessResponse(exchange, Map.of(
                "success", true,
                "message", "获取在线玩家列表成功",
                "onlineCount", onlinePlayers.size(),
                "players", onlinePlayers
        ));
        plugin.getLogger().info("用户获取在线玩家列表成功，当前在线人数：" + onlinePlayers.size());
    }

    // 处理获取玩家周围地图数据请求
    private void handleGetPlayerMap(HttpServerExchange exchange) throws Exception {
        // 仅允许 GET
        if (!"GET".equals(exchange.getRequestMethod().toString())) {
            sendErrorResponse(exchange, StatusCodes.METHOD_NOT_ALLOWED, "仅支持GET请求");
            return;
        }

        // 获取参数
        String token = exchange.getQueryParameters().get("token") != null ? exchange.getQueryParameters().get("token").peekFirst() : null;
        String playerName = exchange.getQueryParameters().get("playerName") != null ? exchange.getQueryParameters().get("playerName").peekFirst() : null;
        String radiusStr = exchange.getQueryParameters().get("radius") != null ? exchange.getQueryParameters().get("radius").peekFirst() : "70";
        int radius = Integer.parseInt(radiusStr);

        if (token == null) {
            sendErrorResponse(exchange, StatusCodes.BAD_REQUEST, "Token不能为空");
            return;
        }

        if (playerName == null) {
            sendErrorResponse(exchange, StatusCodes.BAD_REQUEST, "玩家名称不能为空");
            return;
        }

        // 验证Token
        UserHandler userHandler = plugin.getUserHandler();
        if (userHandler.validateToken(token) == null) {
            sendErrorResponse(exchange, StatusCodes.UNAUTHORIZED, "Token无效或已过期");
            return;
        }

        // 获取玩家
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null) {
            sendErrorResponse(exchange, StatusCodes.NOT_FOUND, "玩家不在线");
            return;
        }

        // 获取玩家周围的地图数据
        Map<String, Object> mapData = getMapData(player, radius);

        // 返回成功响应
        sendSuccessResponse(exchange, Map.of(
                "success", true,
                "message", "获取地图数据成功",
                "mapData", mapData
        ));
        plugin.getLogger().info("用户获取玩家 " + playerName + " 周围地图数据成功");
    }

    // 获取玩家周围的地图数据
    private Map<String, Object> getMapData(Player player, int radius) {
        Location location = player.getLocation();
        World world = location.getWorld();

        // 获取玩家位置
        Map<String, Object> playerPos = Map.of(
                "x", location.getX(),
                "y", location.getY(),
                "z", location.getZ(),
                "yaw", location.getYaw(),
                "pitch", location.getPitch()
        );

        // 获取玩家周围的方块数据（简化版本，只获取方块类型）
        List<Map<String, Object>> blocks = new ArrayList<>();
        
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                // 只处理在半径范围内的方块
                if (Math.sqrt(x * x + z * z) > radius) {
                    continue;
                }

                // 获取该位置的最高方块
                int highestY = world.getHighestBlockYAt(location.getBlockX() + x, location.getBlockZ() + z);
                Block block = world.getBlockAt(location.getBlockX() + x, highestY, location.getBlockZ() + z);

                // 添加方块数据
                blocks.add(Map.of(
                        "x", x,
                        "y", highestY - location.getBlockY(),
                        "z", z,
                        "type", block.getType().name(),
                        "material", block.getType().name()
                ));
            }
        }

        return Map.of(
                "playerPosition", playerPos,
                "radius", radius,
                "blocks", blocks,
                "world", world.getName()
        );
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