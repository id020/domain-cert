package com.acme.domaincert.service;

import com.acme.domaincert.domain.DomainAsset;
import com.acme.domaincert.repository.DomainRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** 每日自动更新域名注册信息与 DNS 记录。 */
@Component
public class DomainRefreshScheduler {

    private final DomainRepository domains;
    private final DomainLookupService lookupService;

    public DomainRefreshScheduler(DomainRepository domains, DomainLookupService lookupService) {
        this.domains = domains;
        this.lookupService = lookupService;
    }

    /** 每天凌晨 02:30（上海时区）执行；逐条处理以免单个资产异常中断整批刷新。 */
    @Scheduled(cron = "0 30 2 * * *", zone = "Asia/Shanghai")
    @Transactional
    public void refreshAllDomains() {
        for (DomainAsset domain : domains.findAll()) {
            try {
                domain.applyLookup(lookupService.lookup(domain.getName()), Instant.now());
                domains.save(domain);
            } catch (Exception exception) {
                // 记录失败时间和状态；后续周期仍会继续尝试该域名。
                domain.applyLookup(new DomainAsset.LookupResult(null, null, null, null, "[]",
                        "FAILED", "自动刷新失败，请查看服务日志"), Instant.now());
                domains.save(domain);
            }
        }
    }
}
