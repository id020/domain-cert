package com.acme.domaincert.service;

import com.acme.domaincert.domain.DomainAsset;
import com.acme.domaincert.repository.DomainRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** 将耗时的公共注册与 DNS 查询放到后台执行，避免创建接口长时间无响应。 */
@Service
public class DomainLookupJob {

    private static final Logger logger = LoggerFactory.getLogger(DomainLookupJob.class);

    private final DomainRepository domains;
    private final DomainLookupService lookupService;

    public DomainLookupJob(DomainRepository domains, DomainLookupService lookupService) {
        this.domains = domains;
        this.lookupService = lookupService;
    }

    /** 查询成功或失败都回写状态，列表页可通过刷新看到最新结果。 */
    @Async
    @Transactional
    public void refresh(Long domainId) {
        DomainAsset domain = domains.findById(domainId).orElse(null);
        if (domain == null) return;
        try {
            domain.applyLookup(lookupService.lookup(domain.getName()), Instant.now());
        } catch (Exception exception) {
            logger.warn("域名公开信息查询失败：{}", domain.getName(), exception);
            domain.applyLookup(new DomainAsset.LookupResult(null, null, null, null, "[]",
                    "FAILED", "查询服务暂不可用，请稍后手动刷新"), Instant.now());
        }
        // findById 返回的是当前事务内的托管实体，提交时由脏检查更新，避免 merge 在并发删除后重新插入。
    }
}
