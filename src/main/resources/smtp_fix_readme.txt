# SMTP邮件发送问题修复说明

## 问题描述
服务器日志中出现SMTP邮件发送失败错误：
```
com.sun.mail.util.MailConnectException: Couldn't connect to host, port: smtp.gmail.com, 25; timeout -1;
```

## 根本原因
1. Gmail等邮件服务通常使用端口587，需要STARTTLS加密
2. 代码中缺少对STARTTLS加密方式的支持
3. 配置默认端口与实际使用端口不匹配

## 修复内容

### 1. MailUtils.java (行号48-56)
添加了STARTTLS加密支持：
```java
// 处理不同的加密方式
if (ssl) {
    // SSL方式（通常端口465）
    props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
    props.put("mail.smtp.socketFactory.port", port);
} else if (port == 587) {
    // STARTTLS方式（通常端口587，如Gmail）
    props.put("mail.smtp.starttls.enable", "true");
}
```

### 2. ServerHandler.java (行号340)
更新默认SMTP端口：
```java
"port", config.getInt("smtp.port", 587),  // 从25改为587
```

## 配置建议
对于Gmail，推荐使用以下配置：
```yaml
smtp:
  enable: true
  host: "smtp.gmail.com"
  port: 587
  ssl: false  # 使用STARTTLS，不需要SSL
  username: "your_gmail@gmail.com"
  password: "your_app_password"  # 使用应用专用密码
  sender: "your_gmail@gmail.com"
```

## 注意事项
1. 确保邮件账号启用了SMTP服务
2. Gmail需要使用应用专用密码（开启两步验证后生成）
3. 检查服务器防火墙是否允许连接到SMTP服务器
4. 部分邮件服务可能需要额外的安全设置