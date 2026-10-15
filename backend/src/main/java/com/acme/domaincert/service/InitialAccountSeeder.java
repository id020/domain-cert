package com.acme.domaincert.service;

import com.acme.domaincert.domain.UserAccount;
import com.acme.domaincert.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 首次启动时初始化三个内置角色账户。
 *
 * 只有 users 表完全为空时才执行初始化；已有账户不会被 YAML 覆盖，防止重启时重置用户口令。
 */
@Component
public class InitialAccountSeeder implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(InitialAccountSeeder.class);

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    public InitialAccountSeeder(UserAccountRepository users, PasswordEncoder passwordEncoder,
                               Environment environment) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    /** 等 Flyway 和 JPA 初始化结束后，在数据库事务中写入首批账户。 */
    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        long existingCount = users.count();
        if (existingCount > 0) {
            // 保留数据库中已有的账户与口令，不因应用重启而重置。
            LOGGER.info("users 表已有 {} 个账户，跳过首次账户初始化", existingCount);
            return;
        }

        List<UserAccount> initialAccounts = List.of(
                account("admin", "app.bootstrap-users.admin.password", "管理员", "ADMIN"),
                account("operator", "app.bootstrap-users.operator.password", "运维", "OPERATOR"),
                account("viewer", "app.bootstrap-users.viewer.password", "只读用户", "VIEWER"));
        users.saveAllAndFlush(initialAccounts);
        LOGGER.info("users 表为空，已初始化 admin、operator、viewer 三个账户（数据库仅保存 BCrypt 哈希）");
    }

    /** 校验部署密码并生成 BCrypt 哈希，保证数据库从首次写入起不包含明文口令。 */
    private UserAccount account(String username, String passwordProperty, String displayName, String role) {
        String password = environment.getProperty(passwordProperty);
        if (password == null || password.length() < 16 || isPlaceholder(password)) {
            throw new IllegalStateException(passwordProperty + " 必须在 application.yml 中设置为至少 16 位的有效密码");
        }
        return new UserAccount(username, passwordEncoder.encode(password), displayName, role);
    }

    /** 拒绝常见示例文本，防止把文档占位密码初始化成可登录账户。 */
    private boolean isPlaceholder(String value) {
        String normalized = value.toLowerCase();
        return normalized.contains("replace-with")
                || normalized.contains("change-me")
                || normalized.contains("changeme")
                || normalized.contains("example-password");
    }
}
