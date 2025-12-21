package com.server.data.model;

import com.server.util.TimeUtils;

/**
 * QQ绑定信息模型类
 * 对应JSON结构：{"qqOpenId":"openid123","qqUnionId":"unionid456","nickname":"玩家昵称","avatarUrl":"头像URL","bindTime":"2025-12-19 09:00"}
 */
public class QQBinding {
    // QQ开放平台OpenID
    private String qqOpenId;
    // QQ开放平台UnionID
    private String qqUnionId;
    // QQ昵称
    private String nickname;
    // QQ头像URL
    private String avatarUrl;
    // 绑定时间
    private String bindTime;

    // 无参构造
    public QQBinding() {}

    // 有参构造
    public QQBinding(String qqOpenId, String qqUnionId, String nickname, String avatarUrl) {
        this.qqOpenId = qqOpenId;
        this.qqUnionId = qqUnionId;
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
        this.bindTime = TimeUtils.getCurrentTimeStr();
    }

    // ====================== Getter/Setter ======================
    public String getQqOpenId() {
        return qqOpenId;
    }

    public void setQqOpenId(String qqOpenId) {
        this.qqOpenId = qqOpenId;
    }

    public String getQqUnionId() {
        return qqUnionId;
    }

    public void setQqUnionId(String qqUnionId) {
        this.qqUnionId = qqUnionId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getBindTime() {
        return bindTime;
    }

    public void setBindTime(String bindTime) {
        this.bindTime = bindTime;
    }
}