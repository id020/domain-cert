package com.acme.domaincert.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** 证书版本材料；数据库只保存经过 AES-GCM 加密的私钥。 */
@Entity
@Table(name = "certificate_versions")
public class CertificateVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificate_id", nullable = false)
    private CertificateAsset certificate;

    @Column(nullable = false)
    private int versionNumber;

    @Column(name = "encrypted_private_key", nullable = false, columnDefinition = "mediumtext")
    private String encryptedPrivateKey;

    @Column(name = "certificate_pem", nullable = false, columnDefinition = "mediumtext")
    private String certificatePem;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected CertificateVersion() { }

    public CertificateVersion(CertificateAsset certificate, int versionNumber,
                              String encryptedPrivateKey, String certificatePem) {
        this.certificate = certificate;
        this.versionNumber = versionNumber;
        this.encryptedPrivateKey = encryptedPrivateKey;
        this.certificatePem = certificatePem;
    }

    public Long getId() { return id; }
    public int getVersionNumber() { return versionNumber; }
    public String getEncryptedPrivateKey() { return encryptedPrivateKey; }
    public String getCertificatePem() { return certificatePem; }
    public Instant getCreatedAt() { return createdAt; }

    /** 密钥轮换时更新本版本的 AES-GCM 密文，不改变证书链和版本元数据。 */
    public void updateEncryptedPrivateKey(String encryptedPrivateKey) {
        this.encryptedPrivateKey = encryptedPrivateKey;
    }
}
