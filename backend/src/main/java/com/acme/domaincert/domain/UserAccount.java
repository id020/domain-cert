package com.acme.domaincert.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 持久化登录账户。
 *
 * 当前登录名沿用 users.email 列保存，以兼容已有数据库结构；首版内置登录名为 admin、operator 和 viewer。
 */
@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录名存放在既有 email 列中，保留数据库结构兼容性。 */
    @Column(name = "email", nullable = false, unique = true, length = 254)
    private String username;

    /** 数据库只保存 BCrypt 哈希，不保存可逆或明文口令。 */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    /** 角色使用 ADMIN、OPERATOR、VIEWER 等大写值。 */
    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /** 读取数据库默认创建时间，仅用于用户管理列表展示。 */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** 创建时间由数据库的 CURRENT_TIMESTAMP 默认值维护。 */
    protected UserAccount() { }

    public UserAccount(String username, String passwordHash, String displayName, String role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.enabled = true;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() { return createdAt; }

    /** 管理员更新展示名称和权限角色；登录名保持稳定，避免审计主体改变。 */
    public void updateProfile(String displayName, String role) {
        this.displayName = displayName;
        this.role = role;
    }

    /** 管理员可禁用账户，但不能绕过用户服务的最后管理员保护。 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** 只保存密码哈希，明文密码不进入实体或审计记录。 */
    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
