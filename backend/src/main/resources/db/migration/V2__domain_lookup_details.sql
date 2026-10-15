-- 域名信息刷新页需要保存 DNS 汇总与外部查询诊断信息。
ALTER TABLE domains
    ADD COLUMN dns_records_json LONGTEXT NULL,
    ADD COLUMN lookup_message VARCHAR(500) NULL;

-- 确保升级前创建的记录也有可读取的空 DNS 结果。
UPDATE domains
SET dns_records_json = '[]'
WHERE dns_records_json IS NULL OR dns_records_json = '';

ALTER TABLE domains
    MODIFY COLUMN dns_records_json LONGTEXT NOT NULL;

-- 域名专属邮件收件人最多容纳五个邮箱地址。
ALTER TABLE domains
    MODIFY COLUMN alert_recipients VARCHAR(1200) NULL;
