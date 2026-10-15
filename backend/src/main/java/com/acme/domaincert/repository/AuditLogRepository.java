package com.acme.domaincert.repository;

import com.acme.domaincert.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/** 审计记录的持久化入口。 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findTop200ByOrderByCreatedAtDesc();
}
