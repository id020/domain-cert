package com.acme.domaincert.repository;

import com.acme.domaincert.domain.CertificateVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 保存加密证书版本并查询指定证书的最新版本。 */
public interface CertificateVersionRepository extends JpaRepository<CertificateVersion, Long> {
    Optional<CertificateVersion> findTopByCertificateIdOrderByVersionNumberDesc(Long certificateId);
}
