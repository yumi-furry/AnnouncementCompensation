package com.server.util;

import org.bukkit.configuration.Configuration;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

/**
 * 邮件发送工具类
 */
public class MailUtils {
    /**
     * 发送邮件
     * @param config 插件配置
     * @param to 收件人邮箱
     * @param subject 邮件主题
     * @param content 邮件内容
     * @return 是否发送成功
     */
    public static boolean sendMail(Configuration config, String to, String subject, String content) {
        // 检查SMTP配置是否完整
        if (!config.contains("smtp.host") || !config.contains("smtp.port") ||
            !config.contains("smtp.username") || !config.contains("smtp.password") ||
            !config.contains("smtp.sender") || !config.contains("smtp.enable") ||
            !config.getBoolean("smtp.enable")) {
            return false;
        }

        // 获取SMTP配置
        String host = config.getString("smtp.host");
        int port = config.getInt("smtp.port");
        String username = config.getString("smtp.username");
        String password = config.getString("smtp.password");
        String sender = config.getString("smtp.sender");
        String protocol = config.getString("smtp.protocol", "smtp");
        boolean ssl = config.getBoolean("smtp.ssl", false);

        // 配置SMTP服务器
        Properties props = new Properties();
        props.put("mail.transport.protocol", protocol);
        props.put("mail.host", host);
        props.put("mail.port", port);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.timeout", "5000");

        // 处理不同的加密方式
        if (ssl) {
            // SSL方式（通常端口465）
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.port", port);
        } else if (port == 587) {
            // STARTTLS方式（通常端口587，如Gmail）
            props.put("mail.smtp.starttls.enable", "true");
        }

        // 创建会话
        Session session = Session.getDefaultInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        try {
            // 创建邮件对象
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(sender));
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject(subject);
            message.setText(content);

            // 发送邮件
            Transport.send(message);
            return true;
        } catch (MessagingException e) {
            e.printStackTrace();
            return false;
        }
    }
}
