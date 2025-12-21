package com.server.data.model;

import com.google.gson.annotations.SerializedName;
import com.server.util.TimeUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 公告模型类
 * 对应JSON结构：{"id":"xxx","name":"公告名称","content":"&a欢迎公告","sendTime":"2025-12-20 10:00","createTime":"2025-12-19 09:00","sent":false,"priority":10,"readStatus":{"玩家UUID":true}}
 */
public class Announcement {
    // 数据库ID（transient：JSON序列化/反序列化时忽略）
    private transient int id;
    // 公告唯一ID（UUID生成）
    @SerializedName("id")
    private String announcementId;
    // 公告名称
    private String name;
    // 公告标题
    private String title;
    // 公告内容（支持&颜色符）
    private String content;
    // 定时发送时间（格式：yyyy-MM-dd HH:mm，null/空表示立即发送）
    private String sendTime;
    // 创建时间
    private String createTime;
    // 更新时间
    private String updatedAt;
    // 作者
    private String author;
    // 是否已发送
    private boolean sent = false;
    // 公告优先级（数值越大，优先级越高）
    private int priority = 0;
    // 玩家已读状态（Key：玩家UUID，Value：是否已读）
    private Map<String, Boolean> readStatus = new HashMap<>();

    // 无参构造
    public Announcement() {}

    // 有参构造
    public Announcement(String name, String content, String sendTime) {
        this.name = name;
        this.title = name;
        this.content = content;
        this.sendTime = sendTime;
        this.createTime = TimeUtils.getCurrentTimeStr();
        this.updatedAt = TimeUtils.getCurrentTimeStr();
    }

    // 包含优先级的有参构造
    public Announcement(String name, String content, String sendTime, int priority) {
        this.name = name;
        this.title = name;
        this.content = content;
        this.sendTime = sendTime;
        this.priority = priority;
        this.createTime = TimeUtils.getCurrentTimeStr();
        this.updatedAt = TimeUtils.getCurrentTimeStr();
    }

    // 数据库构造方法
    public Announcement(int id, String title, String content, String author, String updatedAt) {
        this.id = id;
        this.title = title;
        this.name = title;
        this.content = content;
        this.author = author;
        this.updatedAt = updatedAt;
        this.createTime = TimeUtils.getCurrentTimeStr();
    }

    /**
     * 判断是否到发送时间（定时公告专用）
     */
    public boolean isTimeToSend() {
        if (sendTime == null || sendTime.isEmpty()) {
            return false; // 立即发送的公告已在创建时处理
        }
        // 对比当前时间与发送时间（格式：yyyy-MM-dd HH:mm）
        return TimeUtils.getCurrentTimeStr().compareTo(sendTime) >= 0;
    }

    // ====================== Getter/Setter ======================
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    // 向后兼容的方法
    public String getIdString() {
        return announcementId;
    }

    public void setId(String id) {
        this.announcementId = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    // 向后兼容的别名方法
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.title = name;
        this.name = name;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSendTime() {
        return sendTime;
    }

    public void setSendTime(String sendTime) {
        this.sendTime = sendTime;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public boolean isSent() {
        return sent;
    }

    public void setSent(boolean sent) {
        this.sent = sent;
    }

    public Map<String, Boolean> getReadStatus() {
        return readStatus;
    }

    public void setReadStatus(Map<String, Boolean> readStatus) {
        this.readStatus = readStatus;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getCreatedAt() {
        return createTime;
    }

    public void setCreatedAt(String createdAt) {
        this.createTime = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}