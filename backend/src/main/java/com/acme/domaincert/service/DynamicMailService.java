package com.acme.domaincert.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Properties;

/** 每次发信时读取数据库中的 SMTP 配置，管理员保存后立即生效，无需重启服务。 */
@Service
public class DynamicMailService {

    private final SystemSettingsService settings;

    public DynamicMailService(SystemSettingsService settings) { this.settings = settings; }

    /** 使用当前 SMTP 配置发送指定主题和正文；连接口令只在内存中短暂使用。 */
    public void send(String recipient, String subject, String body) {
        SystemSettingsService.MailSettings config = settings.mailSettings();
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.host());
        sender.setPort(config.port());
        sender.setUsername(config.username());
        sender.setPassword(config.password());
        sender.setDefaultEncoding("UTF-8");

        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", Boolean.toString(config.auth()));
        properties.put("mail.smtp.starttls.enable", Boolean.toString(config.startTls()));
        properties.put("mail.smtp.starttls.required", Boolean.toString(config.startTls()));
        properties.put("mail.smtp.connectiontimeout", "5000");
        properties.put("mail.smtp.timeout", "5000");
        properties.put("mail.smtp.writetimeout", "5000");

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(config.from());
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
    }
}
