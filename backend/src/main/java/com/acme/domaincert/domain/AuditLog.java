package com.acme.domaincert.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

/** 记录资产变更行为，供管理员审查域名和证书的操作历史。 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String actorEmail;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(length = 40)
    private String assetType;

    private Long assetId;

    @Lob
    private String details;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected AuditLog() { }

    public AuditLog(String actorEmail, String action, String assetType, Long assetId, String details) {
        this.actorEmail = actorEmail;
        this.action = action;
        this.assetType = assetType;
        this.assetId = assetId;
        this.details = details;
    }

    public Long getId() { return id; }
    public String getActorEmail() { return actorEmail; }
    public String getAction() { return action; }
    public String getAssetType() { return assetType; }
    public Long getAssetId() { return assetId; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
