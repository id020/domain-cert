package com.acme.domaincert.repository;

import com.acme.domaincert.domain.CertificateAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/** 证书元数据的数据库访问接口。 */
public interface CertificateRepository extends JpaRepository<CertificateAsset, Long> {
    Optional<CertificateAsset> findFirstByCommonNameIgnoreCase(String commonName);
}
