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

/** X.509 证书资产元数据；私钥只保存于单独加密的版本记录。 */
@Entity
@Table(name = "certificates")
public class CertificateAsset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 253)
    private String commonName;

    @Column(length = 500)
    private String domains;

    @Column(nullable = false, length = 24)
    private String status = "PENDING";

    private Instant validFrom;
    private Instant validUntil;

    @Column(length = 80)
    private String issuer;

    @Column(length = 24)
    private String challengeType;

    private boolean autoRenew = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domain_id")
    private DomainAsset domain;

    protected CertificateAsset() { }

    /** 根据已解析证书建立资产记录。 */
    public CertificateAsset(String commonName, String domains, Instant validFrom, Instant validUntil, String issuer) {
        this.commonName = commonName;
        this.domains = domains;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.issuer = issuer;
        this.status = "ACTIVE";
        this.autoRenew = false;
    }

    /** 切换自动续期标记；手工上传证书默认不启用自动续期。 */
    public void setAutoRenew(boolean autoRenew) {
        this.autoRenew = autoRenew;
    }

    /** 上传新版本时更新可展示的证书有效期、SAN 与签发者信息。 */
    public void replaceMetadata(String domains, Instant validFrom, Instant validUntil, String issuer) {
        this.domains = domains;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.issuer = issuer;
        this.status = "ACTIVE";
    }

    public Long getId() { return id; }
    public String getCommonName() { return commonName; }
    public String getDomains() { return domains; }
    public String getStatus() { return status; }
    public Instant getValidFrom() { return validFrom; }
    public Instant getValidUntil() { return validUntil; }
    public String getIssuer() { return issuer; }
    public String getChallengeType() { return challengeType; }
    public boolean isAutoRenew() { return autoRenew; }
}
