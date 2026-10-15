package com.acme.domaincert.service;

import com.acme.domaincert.domain.CertificateVersion;
import com.acme.domaincert.domain.SystemSetting;
import com.acme.domaincert.repository.CertificateVersionRepository;
import com.acme.domaincert.repository.SystemSettingRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** 管理动态邮件配置和证书数据密钥，并确保敏感设置加密持久化。 */
@Service
public class SystemSettingsService {

    private static final String CERTIFICATE_KEY = "certificate.encryption-key";
    private static final String SMTP_PASSWORD = "mail.password";

    private final SystemSettingRepository settings;
    private final CertificateVersionRepository versions;
    private final AuditService auditService;
    private final Environment environment;
    private final byte[] protectionKey;

    public SystemSettingsService(SystemSettingRepository settings, CertificateVersionRepository versions,
                                 AuditService auditService, Environment environment) {
        this.settings = settings;
        this.versions = versions;
        this.auditService = auditService;
        this.environment = environment;
        String rootKey = environment.getRequiredProperty("app.settings.protection-key");
        this.protectionKey = decodeAesKey(rootKey, "系统配置保护密钥");
    }

    /** 首次升级后将 YAML 默认值迁入数据库；之后重启不会覆盖管理员动态修改的值。 */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedDefaults() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("mail.host", property("spring.mail.host", ""));
        defaults.put("mail.port", property("spring.mail.port", "587"));
        defaults.put("mail.username", property("spring.mail.username", ""));
        defaults.put("mail.auth", property("spring.mail.properties.mail.smtp.auth", "true"));
        defaults.put("mail.starttls", property("spring.mail.properties.mail.smtp.starttls.enable", "true"));
        defaults.put("mail.from", property("app.mail.from", "domain-cert@localhost"));
        defaults.put("mail.recipients", property("app.alert.recipients", ""));
        settings.findById(SMTP_PASSWORD).orElseGet(() -> saveSecret(SMTP_PASSWORD,
                property("spring.mail.password", "")));
        settings.findById(CERTIFICATE_KEY).orElseGet(() -> saveSecret(CERTIFICATE_KEY,
                property("app.certificate.encryption-key", "")));
        defaults.forEach((key, value) -> settings.findById(key).orElseGet(() -> settings.save(new SystemSetting(key, value))));
    }

    /** 读取供管理员编辑的配置视图，不返回任何明文口令或 AES 密钥。 */
    @Transactional(readOnly = true)
    public SettingsView view() {
        return new SettingsView(value("mail.host"), integerValue("mail.port"), value("mail.username"),
                hasSecret(SMTP_PASSWORD), booleanValue("mail.auth"), booleanValue("mail.starttls"),
                value("mail.from"), value("mail.recipients"), hasSecret(CERTIFICATE_KEY));
    }

    /** 返回邮件发送所需设置；SMTP 口令仅在服务内解密，不进入 API。 */
    @Transactional(readOnly = true)
    public MailSettings mailSettings() {
        return new MailSettings(value("mail.host"), integerValue("mail.port"), value("mail.username"),
                secret(SMTP_PASSWORD), booleanValue("mail.auth"), booleanValue("mail.starttls"),
                value("mail.from"), value("mail.recipients"));
    }

    /** 仅服务内部读取当前证书数据密钥；任何控制器响应都不包含此值。 */
    @Transactional(readOnly = true)
    public String certificateEncryptionKey() { return secret(CERTIFICATE_KEY); }

    /** 动态保存邮件配置；更换证书密钥时在同一事务内重加密所有历史私钥。 */
    @Transactional
    public SettingsView update(SettingsUpdate request, Authentication actor) {
        put("mail.host", request.smtpHost().trim());
        put("mail.port", Integer.toString(request.smtpPort()));
        put("mail.username", request.smtpUsername().trim());
        put("mail.auth", Boolean.toString(request.smtpAuth()));
        put("mail.starttls", Boolean.toString(request.startTls()));
        put("mail.from", request.fromAddress().trim());
        put("mail.recipients", request.globalRecipients() == null ? "" : request.globalRecipients().trim());

        if (request.smtpPassword() != null && !request.smtpPassword().isBlank()) {
            putSecret(SMTP_PASSWORD, request.smtpPassword());
        }
        if (request.certificateEncryptionKey() != null && !request.certificateEncryptionKey().isBlank()) {
            byte[] newKey = decodeAesKey(request.certificateEncryptionKey().trim(), "证书私钥加密密钥");
            byte[] oldKey = decodeAesKey(secret(CERTIFICATE_KEY), "当前证书私钥加密密钥");
            if (!java.util.Arrays.equals(oldKey, newKey)) rotateCertificateKeys(oldKey, newKey);
            putSecret(CERTIFICATE_KEY, Base64.getEncoder().encodeToString(newKey));
        }
        auditService.record(actor, "SYSTEM_SETTINGS_UPDATED", "SYSTEM", null,
                "更新 SMTP、提醒收件人或证书私钥加密配置");
        return view();
    }

    /** 将全部证书版本从旧数据密钥迁移到新密钥，失败时事务回滚。 */
    private void rotateCertificateKeys(byte[] oldKey, byte[] newKey) {
        try {
            for (CertificateVersion version : versions.findAll()) {
                byte[] privateKey = AesGcmCrypto.decrypt(version.getEncryptedPrivateKey(), oldKey);
                version.updateEncryptedPrivateKey(AesGcmCrypto.encrypt(privateKey, newKey));
            }
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "证书私钥密钥轮换失败，设置未保存", exception);
        }
    }

    private String property(String key, String fallback) { return environment.getProperty(key, fallback); }

    private String value(String key) {
        return settings.findById(key).map(SystemSetting::getValue).orElse("");
    }

    private int integerValue(String key) {
        try { return Integer.parseInt(value(key)); }
        catch (NumberFormatException exception) { return 587; }
    }

    private boolean booleanValue(String key) { return Boolean.parseBoolean(value(key)); }

    private boolean hasSecret(String key) {
        return settings.findById(key).map(setting -> !setting.getValue().isBlank()).orElse(false);
    }

    private String secret(String key) {
        return settings.findById(key).map(setting -> {
            try {
                return new String(AesGcmCrypto.decrypt(setting.getValue(), protectionKey), StandardCharsets.UTF_8);
            } catch (Exception exception) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "动态敏感配置解密失败", exception);
            }
        }).orElse("");
    }

    private SystemSetting saveSecret(String key, String clearText) {
        try {
            return settings.save(new SystemSetting(key,
                    AesGcmCrypto.encrypt(clearText.getBytes(StandardCharsets.UTF_8), protectionKey)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法加密初始化配置", exception);
        }
    }

    private void put(String key, String value) {
        settings.findById(key).ifPresentOrElse(setting -> setting.updateValue(value),
                () -> settings.save(new SystemSetting(key, value)));
    }

    private void putSecret(String key, String clearText) {
        try {
            String encrypted = AesGcmCrypto.encrypt(clearText.getBytes(StandardCharsets.UTF_8), protectionKey);
            settings.findById(key).ifPresentOrElse(setting -> setting.updateValue(encrypted),
                    () -> settings.save(new SystemSetting(key, encrypted)));
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "敏感配置加密失败", exception);
        }
    }

    private byte[] decodeAesKey(String encoded, String name) {
        try {
            byte[] key = Base64.getDecoder().decode(encoded);
            AesGcmCrypto.validateKey(key);
            return key;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + "必须是有效的 32 字节 Base64 AES-256 密钥", exception);
        }
    }

    /** 配置响应中的状态字段只说明密钥是否存在，不泄露密钥或 SMTP 密码。 */
    public record SettingsView(String smtpHost, int smtpPort, String smtpUsername, boolean smtpPasswordConfigured,
                               boolean smtpAuth, boolean startTls, String fromAddress, String globalRecipients,
                               boolean certificateEncryptionKeyConfigured) { }

    /** 邮件发送服务内部使用的动态配置对象。 */
    public record MailSettings(String host, int port, String username, String password,
                               boolean auth, boolean startTls, String from, String recipients) { }

    /** 管理员保存设置的请求；密码和新 AES 密钥可以留空以保留现有值。 */
    public record SettingsUpdate(String smtpHost, int smtpPort, String smtpUsername, String smtpPassword,
                                 boolean smtpAuth, boolean startTls, String fromAddress, String globalRecipients,
                                 String certificateEncryptionKey) { }
}
