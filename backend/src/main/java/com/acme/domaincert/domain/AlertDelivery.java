package com.acme.domaincert.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;

/** 单次到期提醒投递记录；资产、提醒档位、到期日和收件人共同保证幂等。 */
@Entity
@Table(name = "alert_deliveries", uniqueConstraints = @UniqueConstraint(
        name = "uq_alert_once", columnNames = {"asset_type", "asset_id", "alert_days", "expiration_date", "recipient"}))
public class AlertDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_type", nullable = false, length = 20)
    private String assetType;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "alert_days", nullable = false)
    private int alertDays;

    @Column(name = "expiration_date", nullable = false)
    private LocalDate expirationDate;

    @Column(nullable = false, length = 254)
    private String recipient;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected AlertDelivery() { }

    public AlertDelivery(String assetType, Long assetId, int alertDays, LocalDate expirationDate, String recipient) {
        this.assetType = assetType;
        this.assetId = assetId;
        this.alertDays = alertDays;
        this.expirationDate = expirationDate;
        this.recipient = recipient;
        this.status = "PENDING";
    }

    /** 更新发送结果；失败记录可以在下一次调度中重试。 */
    public void complete(boolean sent, String error) {
        this.status = sent ? "SENT" : "FAILED";
        this.errorMessage = error;
        this.sentAt = sent ? Instant.now() : null;
    }

    public Long getId() { return id; }
    public String getAssetType() { return assetType; }
    public Long getAssetId() { return assetId; }
    public int getAlertDays() { return alertDays; }
    public LocalDate getExpirationDate() { return expirationDate; }
    public String getRecipient() { return recipient; }
    public String getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getSentAt() { return sentAt; }
}
