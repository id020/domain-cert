CREATE TABLE domains (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 name VARCHAR(253) NOT NULL UNIQUE,
 registered_at DATE NULL,
 expires_at DATE NULL,
 registrar VARCHAR(255) NULL,
 status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
 name_servers VARCHAR(500) NULL,
 lookup_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
 last_refreshed_at TIMESTAMP NULL,
 alert_recipients VARCHAR(120) NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE certificates (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 domain_id BIGINT NULL,
 common_name VARCHAR(253) NOT NULL,
 domains VARCHAR(500) NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
 valid_from TIMESTAMP NULL,
 valid_until TIMESTAMP NULL,
 issuer VARCHAR(80) NULL,
 challenge_type VARCHAR(24) NULL,
 auto_renew BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_cert_domain FOREIGN KEY (domain_id) REFERENCES domains(id) ON DELETE SET NULL
);
CREATE TABLE certificate_versions (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 certificate_id BIGINT NOT NULL,
 version_number INT NOT NULL,
 encrypted_private_key MEDIUMTEXT NOT NULL,
 certificate_pem MEDIUMTEXT NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_version_cert FOREIGN KEY (certificate_id) REFERENCES certificates(id) ON DELETE CASCADE,
 UNIQUE KEY uq_cert_version (certificate_id, version_number)
);
CREATE TABLE users (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 email VARCHAR(254) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL,
 display_name VARCHAR(120) NOT NULL,
 role VARCHAR(20) NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE audit_logs (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 actor_email VARCHAR(254) NOT NULL,
 action VARCHAR(80) NOT NULL,
 asset_type VARCHAR(40) NULL,
 asset_id BIGINT NULL,
 details JSON NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE alert_deliveries (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 asset_type VARCHAR(20) NOT NULL,
 asset_id BIGINT NOT NULL,
 alert_days INT NOT NULL,
 expiration_date DATE NOT NULL,
 recipient VARCHAR(254) NOT NULL,
 status VARCHAR(20) NOT NULL,
 error_message VARCHAR(500) NULL,
 sent_at TIMESTAMP NULL,
 UNIQUE KEY uq_alert_once (asset_type, asset_id, alert_days, expiration_date, recipient)
);
