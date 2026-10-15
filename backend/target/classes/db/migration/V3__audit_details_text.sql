-- 使用长文本承载已序列化的 JSON，避免 JDBC JSON 类型映射差异影响启动校验。
ALTER TABLE audit_logs
    MODIFY COLUMN details LONGTEXT NULL;
