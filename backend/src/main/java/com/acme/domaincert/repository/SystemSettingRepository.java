package com.acme.domaincert.repository;

import com.acme.domaincert.domain.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

/** 按稳定配置键读写系统设置。 */
public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> { }
