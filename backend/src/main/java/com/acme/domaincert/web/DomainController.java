package com.acme.domaincert.web;

import com.acme.domaincert.domain.DomainAsset;
import com.acme.domaincert.repository.DomainRepository;
import com.acme.domaincert.service.DomainLookupJob;
import com.acme.domaincert.service.DomainLookupService;
import com.acme.domaincert.service.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** 域名资产的读写、信息刷新和提醒收件人配置接口。 */
@RestController
@RequestMapping("/api/domains")
public class DomainController {

    private final DomainRepository domains;
    private final DomainLookupService lookupService;
    private final DomainLookupJob lookupJob;
    private final AuditService auditService;

    public DomainController(DomainRepository domains, DomainLookupService lookupService,
                            DomainLookupJob lookupJob, AuditService auditService) {
        this.domains = domains;
        this.lookupService = lookupService;
        this.lookupJob = lookupJob;
        this.auditService = auditService;
    }

    /** 管理员、运维和只读用户都可以查看域名。 */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public List<DomainAsset> list() {
        return domains.findAll();
    }

    /** 先立即创建域名记录，再由后台任务查询公开信息，避免页面等待网络查询完成。 */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public DomainAsset create(@Valid @RequestBody CreateDomain body, Authentication authentication) {
        String name = body.name().toLowerCase().trim();
        if (domains.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "域名已经存在");
        }

        DomainAsset domain = domains.save(new DomainAsset(name));
        auditService.record(authentication, "DOMAIN_CREATED", "DOMAIN", domain.getId(), domain.getName());
        lookupJob.refresh(domain.getId());
        return domain;
    }

    /** 手动刷新单个域名的 RDAP 与 DNS 信息。 */
    @PostMapping("/{id}/refresh")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Transactional
    public DomainAsset refreshOne(@PathVariable Long id, Authentication authentication) {
        DomainAsset refreshed = refresh(findDomain(id));
        auditService.record(authentication, "DOMAIN_REFRESHED", "DOMAIN", refreshed.getId(), refreshed.getName());
        return refreshed;
    }

    /** 手动刷新所有域名；返回每项最新查询状态供前端汇总展示。 */
    @PostMapping("/refresh")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Transactional
    public List<DomainAsset> refreshAll(Authentication authentication) {
        List<DomainAsset> refreshed = domains.findAll().stream().map(this::refresh).toList();
        auditService.record(authentication, "DOMAIN_REFRESHED_ALL", "DOMAIN", null,
                "已刷新 " + refreshed.size() + " 个域名");
        return refreshed;
    }

    /** 为资产配置逗号分隔的独立收件人；空字符串恢复全局默认收件人。 */
    @PutMapping("/{id}/recipients")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public DomainAsset updateRecipients(@PathVariable Long id, @Valid @RequestBody Recipients body,
                                        Authentication authentication) {
        DomainAsset domain = findDomain(id);
        String cleanedRecipients = body.recipients() == null || body.recipients().isBlank()
                ? null
                : body.recipients().trim();
        domain.updateAlertRecipients(cleanedRecipients);
        DomainAsset saved = domains.save(domain);
        auditService.record(authentication, "DOMAIN_RECIPIENTS_UPDATED", "DOMAIN", saved.getId(), "提醒收件人已更新");
        return saved;
    }

    /** 删除域名；证书表通过外键保留资产记录但清空关联关系。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        DomainAsset domain = findDomain(id);
        auditService.record(authentication, "DOMAIN_DELETED", "DOMAIN", domain.getId(), domain.getName());
        domains.delete(domain);
    }

    /** 集中处理查询与持久化，确保每次操作都更新刷新时间。 */
    private DomainAsset refresh(DomainAsset domain) {
        DomainAsset.LookupResult result = lookupService.lookup(domain.getName());
        domain.applyLookup(result, Instant.now());
        // 调用方在事务中读取记录，依靠 JPA 脏检查写入，避免删除竞态导致 detached merge 复活记录。
        return domain;
    }

    /** 统一处理资源不存在的响应，避免各接口复制相同判断。 */
    private DomainAsset findDomain(Long id) {
        return domains.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到该域名"));
    }

    /** 创建请求只接受完整域名，不接受 URL、端口或路径。 */
    public record CreateDomain(
            @NotBlank
            @Pattern(regexp = "(?i)^(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$")
            String name) { }

    /** 收件人可以留空或提供最多五个逗号分隔的电子邮件地址。 */
    public record Recipients(String recipients) {
        public Recipients {
            if (recipients != null && !recipients.isBlank()) {
                String[] emails = recipients.split(",");
                if (emails.length > 5) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "最多配置五个收件人");
                }
                for (String email : emails) {
                    if (!email.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "收件人邮箱格式无效");
                    }
                }
            }
        }
    }
}
