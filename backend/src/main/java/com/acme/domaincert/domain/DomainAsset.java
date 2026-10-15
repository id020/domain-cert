package com.acme.domaincert.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/** 域名资产主表实体。公开查询失败时保留已有值，同时更新查询状态和时间。 */
@Entity
@Table(name = "domains")
public class DomainAsset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 253)
    private String name;

    private LocalDate registeredAt;
    private LocalDate expiresAt;

    @Column(length = 255)
    private String registrar;

    @Column(length = 32)
    private String status = "ACTIVE";

    @Column(length = 500)
    private String nameServers;

    /** 保存 DNS 记录的 JSON 文本，前端可据此展示多种记录类型。 */
    @Lob
    private String dnsRecordsJson = "[]";

    @Column(length = 32)
    private String lookupStatus = "PENDING";

    @Column(length = 500)
    private String lookupMessage;

    private Instant lastRefreshedAt;

    @Column(length = 1200)
    private String alertRecipients;

    protected DomainAsset() { }

    public DomainAsset(String name) {
        this.name = name.toLowerCase().trim();
    }

    /** 将一次外部查询的最新结果应用到域名记录。 */
    public void applyLookup(LookupResult result, Instant refreshedAt) {
        if (result.registeredAt() != null) this.registeredAt = result.registeredAt();
        if (result.expiresAt() != null) this.expiresAt = result.expiresAt();
        if (result.registrar() != null) this.registrar = result.registrar();
        if (result.nameServers() != null && !result.nameServers().isBlank()) {
            this.nameServers = result.nameServers();
        }
        // 查询源暂时失败或没有公开记录时保留最近一次有效 DNS 快照。
        if (result.dnsRecordsJson() != null && !"[]".equals(result.dnsRecordsJson())) {
            this.dnsRecordsJson = result.dnsRecordsJson();
        }
        this.lookupStatus = result.lookupStatus();
        this.lookupMessage = result.message();
        this.lastRefreshedAt = refreshedAt;
    }

    /** 更新该域名专属的提醒收件人，空值表示采用全局收件人。 */
    public void updateAlertRecipients(String recipients) {
        this.alertRecipients = recipients;
    }

    /** 承载 RDAP 与 DNS 查询的合并结果。 */
    public record LookupResult(
            LocalDate registeredAt,
            LocalDate expiresAt,
            String registrar,
            String nameServers,
            String dnsRecordsJson,
            String lookupStatus,
            String message) { }

    public Long getId() { return id; }
    public String getName() { return name; }
    public LocalDate getRegisteredAt() { return registeredAt; }
    public LocalDate getExpiresAt() { return expiresAt; }
    public String getRegistrar() { return registrar; }
    public String getStatus() { return status; }
    public String getNameServers() { return nameServers; }
    public String getDnsRecordsJson() { return dnsRecordsJson; }
    public String getLookupStatus() { return lookupStatus; }
    public String getLookupMessage() { return lookupMessage; }
    public Instant getLastRefreshedAt() { return lastRefreshedAt; }
    public String getAlertRecipients() { return alertRecipients; }
}
