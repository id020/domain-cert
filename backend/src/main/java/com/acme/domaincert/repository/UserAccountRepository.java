package com.acme.domaincert.repository;

import com.acme.domaincert.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

/** 用户账户的数据访问接口，认证时按登录名查找数据库记录。 */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    /** 忽略大小写匹配登录名，避免 admin 与 ADMIN 被误判为不同账户。 */
    Optional<UserAccount> findByUsernameIgnoreCase(String username);

    /** 用户管理页不依赖数据库私钥字段，只展示账户资料和启用状态。 */
    List<UserAccount> findAllByOrderByIdAsc();

    /** 修改、停用或删除管理员前确认是否还存在其他有效管理员。 */
    long countByRoleAndEnabledTrue(String role);
}
