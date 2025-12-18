package com.server.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Gson 工具类（单例模式）
 * 适配 JDK 17 的 LocalDateTime，统一 JSON 格式化规则
 */
public class GsonUtils {
    // 私有化构造，禁止实例化
    private GsonUtils() {}

    // 单例Gson对象（线程安全）
    private static volatile Gson GSON_INSTANCE;

    // 时间格式化器（与TimeUtils保持一致）
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 获取Gson单例实例
     * @return Gson 对象
     */
    public static Gson getGson() {
        // 双重检查锁，保证单例且线程安全
        if (GSON_INSTANCE == null) {
            synchronized (GsonUtils.class) {
                if (GSON_INSTANCE == null) {
                    GSON_INSTANCE = new GsonBuilder()
                            // 美化JSON输出（便于调试）
                            .setPrettyPrinting()
                            // 不调用 serializeNulls()，以忽略 null 字段（减少JSON体积）
                            // 注册LocalDateTime适配器
                            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeTypeAdapter())
                            // 兼容旧版Gson（避免类型转换错误）
                            .disableHtmlEscaping()
                            .create();
                }
            }
        }
        return GSON_INSTANCE;
    }

    /**
     * LocalDateTime 类型适配器（适配JSON序列化/反序列化）
     */
    private static class LocalDateTimeTypeAdapter extends TypeAdapter<LocalDateTime> {
        @Override
        public void write(JsonWriter out, LocalDateTime value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            out.value(value.format(DATE_TIME_FORMATTER));
        }

        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            String value = in.nextString();
            if (value == null || value.isEmpty()) {
                return null;
            }
            return LocalDateTime.parse(value, DATE_TIME_FORMATTER);
        }
    }

    /**
     * 快捷方法：对象转JSON字符串
     * @param obj 任意对象
     * @return JSON字符串
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return "{}";
        }
        return getGson().toJson(obj);
    }

    /**
     * 快捷方法：JSON字符串转对象
     * @param json JSON字符串
     * @param clazz 目标类
     * @param <T> 泛型
     * @return 目标对象
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isEmpty() || clazz == null) {
            return null;
        }
        return getGson().fromJson(json, clazz);
    }
}