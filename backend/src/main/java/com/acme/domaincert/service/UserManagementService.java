package com.acme.domaincert.service;

import com.acme.domaincert.domain.UserAccount;
import com.acme.domaincert.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** 管理用户账户、角色、启用状态和密码，并保护管理员账户不被误锁定。 */
@Service
public class UserManagementService {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final int MAX_BCRYPT_BYTES = 72;

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserManagementService(UserAccountRepository users, PasswordEncoder passwordEncoder,
                                 AuditService auditService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    /** 返回不含密码哈希的用户资料列表。 */
    @Transactional(readOnly = true)
    public List<UserView> list() {
        return users.findAllByOrderByIdAsc().stream().map(UserView::from).toList();
    }

    /** 当前登录账户资料供所有角色查看自己的角色和启用状态。 */
    @Transactional(readOnly = true)
    public UserView current(Authentication actor) {
        return UserView.from(findByUsername(actor.getName()));
    }

    /** 创建新账户，先校验登录名唯一性，再将明文密码编码成 BCrypt 哈希保存。 */
    @Transactional
    public UserView create(String usernameInput, String displayNameInput, String password,
                           String roleInput, Authentication actor) {
        String username = usernameInput.trim();
        if (users.findByUsernameIgnoreCase(username).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "账户名已存在");
        }
        validatePassword(password);
        String role = normalizeRole(roleInput);
        UserAccount account = users.saveAndFlush(new UserAccount(username, passwordEncoder.encode(password),
                displayNameInput.trim(), role));
        auditService.record(actor, "USER_CREATED", "USER", account.getId(),
                "创建账户 " + account.getUsername() + "，角色 " + role);
        return UserView.from(account);
    }

    /** 更新账户显示名称和角色；禁止当前管理员自行降权或移除最后一位管理员。 */
    @Transactional
    public UserView update(Long id, String displayNameInput, String roleInput, Authentication actor) {
        UserAccount account = findById(id);
        String role = normalizeRole(roleInput);
        boolean changingOwnRole = account.getUsername().equalsIgnoreCase(actor.getName())
                && !account.getRole().equals(role);
        if (changingOwnRole) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "不能通过此操作更改自己的角色");
        }
        if (account.isEnabled() && ADMIN_ROLE.equals(account.getRole()) && !ADMIN_ROLE.equals(role)) {
            ensureAnotherAdminExists();
        }
        account.updateProfile(displayNameInput.trim(), role);
        UserAccount saved = users.save(account);
        auditService.record(actor, "USER_PROFILE_UPDATED", "USER", id,
                "更新账户 " + saved.getUsername() + "，角色 " + role);
        return UserView.from(saved);
    }

    /** 启停其他用户；禁止停用当前登录账户和最后一个启用的管理员。 */
    @Transactional
    public UserView setEnabled(Long id, boolean enabled, Authentication actor) {
        UserAccount account = findById(id);
        if (!enabled && account.getUsername().equalsIgnoreCase(actor.getName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "不能停用当前登录账户");
        }
        if (!enabled && account.isEnabled() && ADMIN_ROLE.equals(account.getRole())) {
            ensureAnotherAdminExists();
        }
        account.setEnabled(enabled);
        UserAccount saved = users.save(account);
        auditService.record(actor, enabled ? "USER_ENABLED" : "USER_DISABLED", "USER", id,
                (enabled ? "启用账户 " : "停用账户 ") + saved.getUsername());
        return UserView.from(saved);
    }

    /** 管理员重置其他用户密码；当前账户需使用个人密码修改接口。 */
    @Transactional
    public void resetPassword(Long id, String password, Authentication actor) {
        UserAccount account = findById(id);
        if (account.getUsername().equalsIgnoreCase(actor.getName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请使用个人密码修改功能更改自己的密码");
        }
        validatePassword(password);
        account.changePassword(passwordEncoder.encode(password));
        users.save(account);
        auditService.record(actor, "USER_PASSWORD_RESET", "USER", id, "管理员重置账户 " + account.getUsername() + " 的密码");
    }

    /** 用户验证当前密码后修改自己的密码；审计记录不含任何密码内容。 */
    @Transactional
    public void changeOwnPassword(String currentPassword, String newPassword, Authentication actor) {
        UserAccount account = findByUsername(actor.getName());
        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前密码不正确");
        }
        validatePassword(newPassword);
        if (passwordEncoder.matches(newPassword, account.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码不能与当前密码相同");
        }
        account.changePassword(passwordEncoder.encode(newPassword));
        users.save(account);
        auditService.record(actor, "OWN_PASSWORD_CHANGED", "USER", account.getId(), "用户修改了自己的密码");
    }

    /** 删除用户；不允许删除当前账户或最后一位启用的管理员。 */
    @Transactional
    public void delete(Long id, Authentication actor) {
        UserAccount account = findById(id);
        if (account.getUsername().equalsIgnoreCase(actor.getName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "不能删除当前登录账户");
        }
        if (account.isEnabled() && ADMIN_ROLE.equals(account.getRole())) ensureAnotherAdminExists();
        auditService.record(actor, "USER_DELETED", "USER", id, "删除账户 " + account.getUsername());
        users.delete(account);
    }

    /** 确保被移除的管理员之外至少还有一位已启用管理员。 */
    private void ensureAnotherAdminExists() {
        if (users.countByRoleAndEnabledTrue(ADMIN_ROLE) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "系统至少需要保留一位已启用的管理员");
        }
    }

    /** 将角色名称归一化并限定为系统支持的三种固定角色。 */
    private String normalizeRole(String roleInput) {
        String role = roleInput == null ? "" : roleInput.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ADMIN", "OPERATOR", "VIEWER").contains(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色只能是 ADMIN、OPERATOR 或 VIEWER");
        }
        return role;
    }

    /** BCrypt 只处理前 72 字节；用明确的长度规则避免长密码被静默截断。 */
    private void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "密码至少 12 个字符，且 UTF-8 编码后不能超过 72 字节");
        }
    }

    private UserAccount findById(Long id) {
        return users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到该用户"));
    }

    private UserAccount findByUsername(String username) {
        return users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "当前账户不存在"));
    }

    /** 仅包含可返回给前端的用户字段，密码哈希不进入 JSON。 */
    public record UserView(Long id, String username, String displayName, String role,
                           boolean enabled, java.time.Instant createdAt) {
        static UserView from(UserAccount account) {
            return new UserView(account.getId(), account.getUsername(), account.getDisplayName(),
                    account.getRole(), account.isEnabled(), account.getCreatedAt());
        }
    }
}
