package com.server.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 时间工具类
 * 统一时间格式，适配 JDK 17 的 LocalDateTime
 */
public class TimeUtils {
    // 私有化构造，禁止实例化
    private TimeUtils() {}

    // 全局统一时间格式（yyyy-MM-dd HH:mm）
    public static final String DEFAULT_FORMAT = "yyyy-MM-dd HH:mm";
    private static final DateTimeFormatter DEFAULT_FORMATTER = DateTimeFormatter.ofPattern(DEFAULT_FORMAT);

    /**
     * 获取当前时间字符串（默认格式）
     * @return 格式：yyyy-MM-dd HH:mm
     */
    public static String getCurrentTimeStr() {
        return LocalDateTime.now().format(DEFAULT_FORMATTER);
    }

    /**
     * 获取当前时间字符串（自定义格式）
     * @param format 自定义格式（如 yyyy-MM-dd HH:mm:ss）
     * @return 格式化后的时间字符串
     */
    public static String getCurrentTimeStr(String format) {
        if (format == null || format.isEmpty()) {
            return getCurrentTimeStr();
        }
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(format));
    }

    /**
     * 解析时间字符串为LocalDateTime（默认格式）
     * @param timeStr 时间字符串（yyyy-MM-dd HH:mm）
     * @return LocalDateTime 对象，解析失败返回null
     */
    public static LocalDateTime parseTime(String timeStr) {
        try {
            return LocalDateTime.parse(timeStr, DEFAULT_FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 解析时间字符串为LocalDateTime（自定义格式）
     * @param timeStr 时间字符串
     * @param format 自定义格式
     * @return LocalDateTime 对象，解析失败返回null
     */
    public static LocalDateTime parseTime(String timeStr, String format) {
        if (format == null || format.isEmpty()) {
            return parseTime(timeStr);
        }
        try {
            return LocalDateTime.parse(timeStr, DateTimeFormatter.ofPattern(format));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 比较两个时间字符串的大小（默认格式）
     * @param time1 时间1
     * @param time2 时间2
     * @return 1=time1晚于time2，0=相等，-1=time1早于time2，null=解析失败
     */
    public static Integer compareTime(String time1, String time2) {
        LocalDateTime t1 = parseTime(time1);
        LocalDateTime t2 = parseTime(time2);
        if (t1 == null || t2 == null) {
            return null;
        }
        return t1.compareTo(t2);
    }
}