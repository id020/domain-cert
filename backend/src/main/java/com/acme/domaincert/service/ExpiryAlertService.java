package com.acme.domaincert.service;

import com.acme.domaincert.domain.AlertDelivery;
import com.acme.domaincert.domain.CertificateAsset;
import com.acme.domaincert.domain.DomainAsset;
import com.acme.domaincert.repository.AlertDeliveryRepository;
import com.acme.domaincert.repository.CertificateRepository;
import com.acme.domaincert.repository.DomainRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/** 根据到期档位发送邮件，并为每个收件人保存成功或失败结果。 */
@Service
public class ExpiryAlertService {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final List<Integer> DEFAULT_ALERT_DAYS = List.of(30, 14, 7, 1);

    /** 邮件标题和正文共享此结构，预览、测试邮件与自动提醒使用同一模板。 */
    public record MailContent(String subject, String body) { }

    private final DomainRepository domains;
    private final CertificateRepository certificates;
    private final AlertDeliveryRepository deliveries;
    private final DynamicMailService mailService;
    private final SystemSettingsService systemSettings;

    public ExpiryAlertService(DomainRepository domains, CertificateRepository certificates,
                              AlertDeliveryRepository deliveries, DynamicMailService mailService,
                              SystemSettingsService systemSettings) {
        this.domains = domains;
        this.certificates = certificates;
        this.deliveries = deliveries;
        this.mailService = mailService;
        this.systemSettings = systemSettings;
    }

    /** 每天上午八点执行；所有到期判断均使用用户界面默认的上海时区。 */
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Shanghai")
    public void sendDailyAlerts() {
        LocalDate today = LocalDate.now(DISPLAY_ZONE);
        retryFailedDeliveries();
        for (DomainAsset domain : domains.findAll()) {
            if (domain.getExpiresAt() != null) {
                notifyAsset("DOMAIN", domain.getId(), domain.getName(), domain.getExpiresAt(),
                        recipients(domain.getAlertRecipients()), today);
            }
        }
        for (CertificateAsset certificate : certificates.findAll()) {
            if (certificate.getValidUntil() != null) {
                LocalDate expiration = certificate.getValidUntil().atZone(DISPLAY_ZONE).toLocalDate();
                notifyAsset("CERTIFICATE", certificate.getId(), certificate.getCommonName(), expiration,
                        recipients(null), today);
            }
        }
    }

    /** 先重试历史失败任务，避免 SMTP 短时故障导致错过该到期档位。 */
    private void retryFailedDeliveries() {
        for (AlertDelivery delivery : deliveries.findTop100ByStatusOrderByIdAsc("FAILED")) {
            String name = delivery.getAssetType().equals("DOMAIN")
                    ? domains.findById(delivery.getAssetId()).map(DomainAsset::getName).orElse(null)
                    : certificates.findById(delivery.getAssetId()).map(CertificateAsset::getCommonName).orElse(null);
            if (name != null) {
                deliverOnce(delivery.getAssetType(), delivery.getAssetId(), name,
                        delivery.getExpirationDate(), delivery.getAlertDays(), delivery.getRecipient());
            }
        }
    }

    /** 查看最近邮件投递记录，供后台提醒中心展示发送和故障状态。 */
    @Transactional(readOnly = true)
    public List<AlertDelivery> recentDeliveries() {
        return deliveries.findTop100ByOrderByIdDesc();
    }

    /** 返回一封示例提醒邮件，页面可先检查内容而不触发 SMTP。 */
    public MailContent previewTestMail() {
        return buildMailContent("example.com", LocalDate.now(DISPLAY_ZONE).plusDays(7), 7);
    }

    /** 按提醒中心当前模板发送手动测试邮件；不会写入自动提醒的投递记录。 */
    public void sendTestMail(String recipient) {
        MailContent content = previewTestMail();
        try {
            mailService.send(recipient, content.subject(), content.body());
        } catch (Exception exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "测试邮件发送失败；请检查系统设置中的 SMTP 配置和服务日志", exception);
        }
    }

    private void notifyAsset(String type, Long id, String name, LocalDate expiresAt,
                             List<String> recipients, LocalDate today) {
        int daysRemaining = (int) (expiresAt.toEpochDay() - today.toEpochDay());
        if (!DEFAULT_ALERT_DAYS.contains(daysRemaining)) return;
        for (String recipient : recipients) {
            deliverOnce(type, id, name, expiresAt, daysRemaining, recipient);
        }
    }

    /** 相同资产、到期日、提醒档位和收件人只产生一条提醒记录。 */
    @Transactional
    protected void deliverOnce(String type, Long id, String name, LocalDate expiration,
                               int daysRemaining, String recipient) {
        AlertDelivery delivery = deliveries
                .findByAssetTypeAndAssetIdAndAlertDaysAndExpirationDateAndRecipient(
                        type, id, daysRemaining, expiration, recipient)
                .orElseGet(() -> new AlertDelivery(type, id, daysRemaining, expiration, recipient));
        if ("SENT".equals(delivery.getStatus())) return;

        try {
            MailContent content = buildMailContent(name, expiration, daysRemaining);
            mailService.send(recipient, content.subject(), content.body());
            delivery.complete(true, null);
        } catch (Exception exception) {
            // 详细 SMTP 异常仅留在服务日志；数据库不保存可能含连接信息的原始错误文本。
            String detail = exception.getClass().getSimpleName() + "：SMTP 投递失败，请检查服务日志";
            delivery.complete(false, detail);
        }
        deliveries.save(delivery);
    }

    /** 自动提醒与手动测试共用同一邮件模板，避免预览内容和实际邮件不一致。 */
    private MailContent buildMailContent(String name, LocalDate expiration, int daysRemaining) {
        String subject = "域名与证书到期提醒：" + name;
        String body = name + " 将在 " + daysRemaining + " 天后（"
                + expiration.format(DateTimeFormatter.ISO_LOCAL_DATE) + "）到期。\n"
                + "请登录域名与 SSL 证书管理系统查看资产状态并安排续期。";
        return new MailContent(subject, body);
    }

    /** 域名级收件人覆盖全局收件人；邮箱列表使用逗号分隔。 */
    private List<String> recipients(String override) {
        String value = override == null || override.isBlank() ? systemSettings.mailSettings().recipients() : override;
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }
}
