package com.server.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * 颜色字符处理工具类
 * 适配 Paper 1.19.2 的 Adventure Component 体系
 * 支持 & 颜色符转换为 Component，兼容 RGB 颜色（&x&a&b&c&d&e&f）
 */
public class ColorUtils {
    // 私有化构造，禁止实例化
    private ColorUtils() {}

    // Legacy序列化器（适配&颜色符）
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&') // 颜色符使用&
            .hexColors() // 支持RGB十六进制颜色
            .build();

    /**
     * 将带&颜色符的字符串转换为Adventure Component（Paper 1.19.2 推荐使用）
     * @param text 带&颜色符的字符串（如 &a欢迎&6玩家）
     * @return Component 对象
     */
    public static Component toComponent(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return LEGACY_SERIALIZER.deserialize(text);
    }

    /**
     * 将Component转换为带&颜色符的字符串（用于存储/日志）
     * @param component Component 对象
     * @return 带&颜色符的字符串
     */
    public static String translate(Component component) {
        if (component == null) {
            return "";
        }
        return LEGACY_SERIALIZER.serialize(component);
    }

    /**
     * 快捷方法：直接转换字符串（用于日志输出）
     * @param text 带&颜色符的字符串
     * @return 转换后的字符串（控制台可显示颜色）
     */
    public static String translate(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return LEGACY_SERIALIZER.serialize(toComponent(text));
    }

    /**
     * 获取RGB颜色的TextColor（扩展用）
     * @param rgb RGB值（如 0xFF5500）
     * @return TextColor 对象
     */
    public static TextColor getRGBColor(int rgb) {
        return TextColor.color(rgb);
    }
}