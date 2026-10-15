package com.acme.domaincert.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 系统动态配置；密码和密钥字段在持久化前会使用根密钥加密。 */
@Entity
@Table(name = "system_settings")
public class SystemSetting {

    @Id
    @Column(name = "setting_key", length = 64)
    private String key;

    @Column(name = "setting_value", nullable = false, columnDefinition = "text")
    private String value;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected SystemSetting() { }

    public SystemSetting(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** 更新配置内容；数据库自动维护最后修改时间。 */
    public void updateValue(String value) { this.value = value; }
}
