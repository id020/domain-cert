package com.acme.domaincert.service;

import com.acme.domaincert.domain.AuditLog;
import com.acme.domaincert.repository.AuditLogRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;

/** 统一写入关键用户操作，避免业务控制器直接拼装审计实体。 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogs;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogs, ObjectMapper objectMapper) {
        this.auditLogs = auditLogs;
        this.objectMapper = objectMapper;
    }

    /** 保存当前操作者及资产变更摘要；不记录口令、私钥等敏感数据。 */
    public void record(Authentication authentication, String action, String type, Long id, String details) {
        String actor = authentication == null ? "system" : authentication.getName();
        try {
            String json = objectMapper.writeValueAsString(Map.of("summary", details == null ? "" : details));
            auditLogs.save(new AuditLog(actor, action, type, id, json));
        } catch (Exception exception) {
            // 日志序列化失败不能伪造成功记录，因此让事务回滚并暴露服务端错误。
            throw new IllegalStateException("无法写入审计记录", exception);
        }
    }
}
