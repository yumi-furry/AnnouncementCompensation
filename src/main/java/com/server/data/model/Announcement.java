package com.server.data.model;

import com.server.util.TimeUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 公告模型类
 * 对应JSON结构：{"id":"xxx","name":"公告名称","content":"&a欢迎公告","sendTime":"2025-12-20 10:00","createTime":"2025-12-19 09:00","sent":false,"readStatus":{"玩家UUID":true}}
 */
public class Announcement {
    // 公告唯一ID（UUID生成）
    private String id;
    // 公告名称
    private String name;
    // 公告内容（支持&颜色符）
    private String content;
    // 定时发送时间（格式：yyyy-MM-dd HH:mm，null/空表示立即发送）
    private String sendTime;
    // 创建时间
    private String createTime;
    // 是否已发送
    private boolean sent = false;
    // 玩家已读状态（Key：玩家UUID，Value：是否已读）
    private Map<String, Boolean> readStatus = new HashMap<>();

    // 无参构造
    public Announcement() {}

    // 有参构造
    public Announcement(String name, String content, String sendTime) {
        this.name = name;
        this.content = content;
        this.sendTime = sendTime;
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
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
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
}