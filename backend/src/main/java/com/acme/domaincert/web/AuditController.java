package com.acme.domaincert.web;

import com.acme.domaincert.domain.AuditLog;
import com.acme.domaincert.repository.AuditLogRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 管理员审计日志只读接口。 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditLogRepository auditLogs;

    public AuditController(AuditLogRepository auditLogs) {
        this.auditLogs = auditLogs;
    }

    /** 按时间倒序返回最近日志，单次最多读取 200 条。 */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuditLog> list(@RequestParam(defaultValue = "50") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return auditLogs.findTop200ByOrderByCreatedAtDesc().stream().limit(safeLimit).toList();
    }
}
