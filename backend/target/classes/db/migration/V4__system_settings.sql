-- 中文注释：保存管理员在系统设置页面维护的动态配置；敏感值由应用层加密后写入。
CREATE TABLE system_settings (
    setting_key VARCHAR(64) NOT NULL PRIMARY KEY,
    setting_value TEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
