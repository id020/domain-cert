package com.acme.domaincert.service;

import com.acme.domaincert.domain.UserAccount;
import com.acme.domaincert.repository.UserAccountRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** 从 users 表加载账户、BCrypt 口令哈希及角色，供 Spring Security 完成登录认证。 */
@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserAccountRepository users;

    public DatabaseUserDetailsService(UserAccountRepository users) {
        this.users = users;
    }

    /** 根据用户输入的登录名读取持久化账户；数据库中不存在时按标准认证异常处理。 */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccount account = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("账户不存在"));

        // roles() 自动增加 ROLE_ 前缀，与控制器中的 hasRole/hasAnyRole 检查保持一致。
        return User.withUsername(account.getUsername())
                .password(account.getPasswordHash())
                .roles(account.getRole())
                .disabled(!account.isEnabled())
                .build();
    }
}
