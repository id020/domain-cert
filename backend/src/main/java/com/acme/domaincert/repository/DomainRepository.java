package com.acme.domaincert.repository;

import com.acme.domaincert.domain.DomainAsset;
import org.springframework.data.jpa.repository.JpaRepository;

/** 域名资产的标准增删改查及大小写不敏感重复检查。 */
public interface DomainRepository extends JpaRepository<DomainAsset, Long> {
    boolean existsByNameIgnoreCase(String name);
}
