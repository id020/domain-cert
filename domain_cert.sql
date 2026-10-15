/*
 Navicat Premium Dump SQL

 Source Server         : localhost
 Source Server Type    : MySQL
 Source Server Version : 80046 (8.0.46)
 Source Host           : localhost:3306
 Source Schema         : domain_cert

 Target Server Type    : MySQL
 Target Server Version : 80046 (8.0.46)
 File Encoding         : 65001

 Date: 15/10/2026 10:35:20
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for alert_deliveries
-- ----------------------------
DROP TABLE IF EXISTS `alert_deliveries`;
CREATE TABLE `alert_deliveries` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `asset_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `asset_id` bigint NOT NULL,
  `alert_days` int NOT NULL,
  `expiration_date` date NOT NULL,
  `recipient` varchar(254) COLLATE utf8mb4_general_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `error_message` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `sent_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_alert_once` (`asset_type`,`asset_id`,`alert_days`,`expiration_date`,`recipient`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- Records of alert_deliveries
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for audit_logs
-- ----------------------------
DROP TABLE IF EXISTS `audit_logs`;
CREATE TABLE `audit_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `actor_email` varchar(254) COLLATE utf8mb4_general_ci NOT NULL,
  `action` varchar(80) COLLATE utf8mb4_general_ci NOT NULL,
  `asset_type` varchar(40) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `asset_id` bigint DEFAULT NULL,
  `details` longtext COLLATE utf8mb4_general_ci,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- Records of audit_logs
-- ----------------------------
BEGIN;
INSERT INTO `audit_logs` (`id`, `actor_email`, `action`, `asset_type`, `asset_id`, `details`, `created_at`) VALUES (1, 'admin', 'DOMAIN_REFRESHED_ALL', 'DOMAIN', NULL, '{\"summary\":\"已刷新 0 个域名\"}', '2026-10-14 07:15:53');
INSERT INTO `audit_logs` (`id`, `actor_email`, `action`, `asset_type`, `asset_id`, `details`, `created_at`) VALUES (2, 'admin', 'DOMAIN_CREATED', 'DOMAIN', 1, '{\"summary\":\"catpanel.cn\"}', '2026-10-14 07:16:28');
INSERT INTO `audit_logs` (`id`, `actor_email`, `action`, `asset_type`, `asset_id`, `details`, `created_at`) VALUES (3, 'admin', 'DOMAIN_REFRESHED', 'DOMAIN', 1, '{\"summary\":\"catpanel.cn\"}', '2026-10-14 07:16:39');
INSERT INTO `audit_logs` (`id`, `actor_email`, `action`, `asset_type`, `asset_id`, `details`, `created_at`) VALUES (4, 'admin', 'DOMAIN_CREATED', 'DOMAIN', 2, '{\"summary\":\"codex-check-141026.example.com\"}', '2026-10-14 07:30:43');
INSERT INTO `audit_logs` (`id`, `actor_email`, `action`, `asset_type`, `asset_id`, `details`, `created_at`) VALUES (5, 'admin', 'DOMAIN_DELETED', 'DOMAIN', 2, '{\"summary\":\"codex-check-141026.example.com\"}', '2026-10-14 07:30:46');
INSERT INTO `audit_logs` (`id`, `actor_email`, `action`, `asset_type`, `asset_id`, `details`, `created_at`) VALUES (6, 'admin', 'DOMAIN_CREATED', 'DOMAIN', 3, '{\"summary\":\"baidu.com\"}', '2026-10-14 07:36:48');
INSERT INTO `audit_logs` (`id`, `actor_email`, `action`, `asset_type`, `asset_id`, `details`, `created_at`) VALUES (7, 'admin', 'DOMAIN_DELETED', 'DOMAIN', 3, '{\"summary\":\"baidu.com\"}', '2026-10-14 07:36:49');
INSERT INTO `audit_logs` (`id`, `actor_email`, `action`, `asset_type`, `asset_id`, `details`, `created_at`) VALUES (8, 'admin', 'DOMAIN_REFRESHED', 'DOMAIN', 1, '{\"summary\":\"catpanel.cn\"}', '2026-10-14 08:47:13');
INSERT INTO `audit_logs` (`id`, `actor_email`, `action`, `asset_type`, `asset_id`, `details`, `created_at`) VALUES (9, 'admin', 'DOMAIN_CREATED', 'DOMAIN', 4, '{\"summary\":\"baidu.com\"}', '2026-10-14 08:47:26');
INSERT INTO `audit_logs` (`id`, `actor_email`, `action`, `asset_type`, `asset_id`, `details`, `created_at`) VALUES (10, 'admin', 'DOMAIN_REFRESHED', 'DOMAIN', 1, '{\"summary\":\"catpanel.cn\"}', '2026-10-14 09:41:07');
INSERT INTO `audit_logs` (`id`, `actor_email`, `action`, `asset_type`, `asset_id`, `details`, `created_at`) VALUES (11, 'admin', 'DOMAIN_REFRESHED', 'DOMAIN', 1, '{\"summary\":\"catpanel.cn\"}', '2026-10-14 09:41:07');
INSERT INTO `audit_logs` (`id`, `actor_email`, `action`, `asset_type`, `asset_id`, `details`, `created_at`) VALUES (12, 'admin', 'DOMAIN_RECIPIENTS_UPDATED', 'DOMAIN', 1, '{\"summary\":\"提醒收件人已更新\"}', '2026-10-14 09:41:37');
INSERT INTO `audit_logs` (`id`, `actor_email`, `action`, `asset_type`, `asset_id`, `details`, `created_at`) VALUES (13, 'admin', 'DOMAIN_RECIPIENTS_UPDATED', 'DOMAIN', 1, '{\"summary\":\"提醒收件人已更新\"}', '2026-10-14 10:52:35');
INSERT INTO `audit_logs` (`id`, `actor_email`, `action`, `asset_type`, `asset_id`, `details`, `created_at`) VALUES (14, 'admin', 'CERTIFICATE_VERSION_UPLOADED', 'CERTIFICATE', 1, '{\"summary\":\"upload-test.invalid\"}', '2026-10-14 11:58:15');
INSERT INTO `audit_logs` (`id`, `actor_email`, `action`, `asset_type`, `asset_id`, `details`, `created_at`) VALUES (15, 'admin', 'CERTIFICATE_VERSION_UPLOADED', 'CERTIFICATE', 1, '{\"summary\":\"upload-test.invalid\"}', '2026-10-14 11:58:31');
INSERT INTO `audit_logs` (`id`, `actor_email`, `action`, `asset_type`, `asset_id`, `details`, `created_at`) VALUES (16, 'admin', 'DOMAIN_REFRESHED_ALL', 'DOMAIN', NULL, '{\"summary\":\"已刷新 2 个域名\"}', '2026-10-14 12:01:39');
INSERT INTO `audit_logs` (`id`, `actor_email`, `action`, `asset_type`, `asset_id`, `details`, `created_at`) VALUES (17, 'admin', 'USER_CREATED', 'USER', 4, '{\"summary\":\"创建账户 codex_check_20261014，角色 VIEWER\"}', '2026-10-14 12:11:47');
INSERT INTO `audit_logs` (`id`, `actor_email`, `action`, `asset_type`, `asset_id`, `details`, `created_at`) VALUES (18, 'admin', 'USER_PROFILE_UPDATED', 'USER', 4, '{\"summary\":\"更新账户 codex_check_20261014，角色 OPERATOR\"}', '2026-10-14 12:11:47');
INSERT INTO `audit_logs` (`id`, `actor_email`, `action`, `asset_type`, `asset_id`, `details`, `created_at`) VALUES (19, 'admin', 'USER_DISABLED', 'USER', 4, '{\"summary\":\"停用账户 codex_check_20261014\"}', '2026-10-14 12:11:47');
INSERT INTO `audit_logs` (`id`, `actor_email`, `action`, `asset_type`, `asset_id`, `details`, `created_at`) VALUES (20, 'admin', 'USER_ENABLED', 'USER', 4, '{\"summary\":\"启用账户 codex_check_20261014\"}', '2026-10-14 12:11:47');
INSERT INTO `audit_logs` (`id`, `actor_email`, `action`, `asset_type`, `asset_id`, `details`, `created_at`) VALUES (21, 'admin', 'USER_PASSWORD_RESET', 'USER', 4, '{\"summary\":\"管理员重置账户 codex_check_20261014 的密码\"}', '2026-10-14 12:11:48');
INSERT INTO `audit_logs` (`id`, `actor_email`, `action`, `asset_type`, `asset_id`, `details`, `created_at`) VALUES (22, 'admin', 'USER_DELETED', 'USER', 4, '{\"summary\":\"删除账户 codex_check_20261014\"}', '2026-10-14 12:11:48');
INSERT INTO `audit_logs` (`id`, `actor_email`, `action`, `asset_type`, `asset_id`, `details`, `created_at`) VALUES (23, 'admin', 'OWN_PASSWORD_CHANGED', 'USER', 1, '{\"summary\":\"用户修改了自己的密码\"}', '2026-10-14 13:51:06');
INSERT INTO `audit_logs` (`id`, `actor_email`, `action`, `asset_type`, `asset_id`, `details`, `created_at`) VALUES (24, 'admin', 'USER_DISABLED', 'USER', 2, '{\"summary\":\"停用账户 operator\"}', '2026-10-14 14:24:07');
INSERT INTO `audit_logs` (`id`, `actor_email`, `action`, `asset_type`, `asset_id`, `details`, `created_at`) VALUES (25, 'admin', 'USER_ENABLED', 'USER', 2, '{\"summary\":\"启用账户 operator\"}', '2026-10-14 14:24:12');
INSERT INTO `audit_logs` (`id`, `actor_email`, `action`, `asset_type`, `asset_id`, `details`, `created_at`) VALUES (26, 'admin', 'USER_PASSWORD_RESET', 'USER', 2, '{\"summary\":\"管理员重置账户 operator 的密码\"}', '2026-10-14 14:24:19');
INSERT INTO `audit_logs` (`id`, `actor_email`, `action`, `asset_type`, `asset_id`, `details`, `created_at`) VALUES (27, 'admin', 'DOMAIN_CREATED', 'DOMAIN', 5, '{\"summary\":\"netbors.com\"}', '2026-10-14 14:29:50');
INSERT INTO `audit_logs` (`id`, `actor_email`, `action`, `asset_type`, `asset_id`, `details`, `created_at`) VALUES (28, 'admin', 'DOMAIN_REFRESHED', 'DOMAIN', 5, '{\"summary\":\"netbors.com\"}', '2026-10-14 14:30:02');
INSERT INTO `audit_logs` (`id`, `actor_email`, `action`, `asset_type`, `asset_id`, `details`, `created_at`) VALUES (29, 'admin', 'DOMAIN_DELETED', 'DOMAIN', 4, '{\"summary\":\"baidu.com\"}', '2026-10-14 14:30:18');
COMMIT;

-- ----------------------------
-- Table structure for certificate_versions
-- ----------------------------
DROP TABLE IF EXISTS `certificate_versions`;
CREATE TABLE `certificate_versions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `certificate_id` bigint NOT NULL,
  `version_number` int NOT NULL,
  `encrypted_private_key` mediumtext COLLATE utf8mb4_general_ci NOT NULL,
  `certificate_pem` mediumtext COLLATE utf8mb4_general_ci NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_cert_version` (`certificate_id`,`version_number`),
  CONSTRAINT `fk_version_cert` FOREIGN KEY (`certificate_id`) REFERENCES `certificates` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- Records of certificate_versions
-- ----------------------------
BEGIN;
INSERT INTO `certificate_versions` (`id`, `certificate_id`, `version_number`, `encrypted_private_key`, `certificate_pem`, `created_at`) VALUES (1, 1, 1, 'h1Vryr9IzZoIcSZll1kbZodmeG5TPWUoXmZCexi1Nv+/F6TUmEQm/STHAHCPFnyjeVY7J7K2jMV/BC4wIxBG17nhdwc1rMMQSc+Q7xZ2SnEXyt9vARKQzX1p5SYxpmZSMexwlQka/p4Btcz+sMLxheIp/FFcl4QYHkewgo2fYMHpRrz282jNyh0LI26TZHA8W7OCy05r8HmzQFIWaS8IrHzJY/cAgWe+i4s2RI81bMMdlkL6Wx7H4IEJrtFPotyqPQHvS6W0Rj2PfnKH2GHnr1mP2BeeX/yoESMsdaHToKRzf0eLWE43vS/nbFo5tG60PSS4msnnaOd+TMgGzTtO1v/JwXFbCtdndcv9YGSOrSlEyge4o0IE1nrOfeqQNgiym8nBB+pCT/Oi+ADoOd9PMksa1hPQQyNErvzsLremHGTHW1VjXNggjSrk9a8nHfIOCe6qRhsquZVnI26ErTLdwEoD9mmucvp37wioFYDgPNrrPwQRCqEzfQiFBdk+4kzJGv3sHaEd06lBnaCAtuE9D5JaAK4eG2ZvEjNqrIqivAisYyqXmSUCSf9GMixWoC9AYn1Z4qHhnI2KGXBQfw/IqjlX2GIKgwlgMm4+cyfVai46hAoDoA3US2bkdHVrE2qLqcJBa2A7k1HnA+Ue69ogD369hZiRWR0vAoCFZCRupz6sSWoFuMSEezz1Obo/dxyPu+gyJkccKJ3jzythkRWL2XWYyOVn2+cKtxCR/8fz1BvO4vGNzAah6OyHeFjHvMBTK0bgH3nu1XmeRNVH3SIox9+HzbwQSF1BtbCZRyKT60L6LikvljsP3YVBrGa3TTsivep6eUqiu1tb5L29UftVptKRO3wi9UXkl8gmK8NTKF7laMUp+yraid/3bJaZ2OOFh08t6EFJTD56kymiPgzYWWzjdtxKrT2AFdzs+lNwEkZGXF6DtRbxJDJ0WyBXMr3CB0wCjtFQ4Qr8CjFCYy63zOerbP1sA8SaT7sdiSfNPVUyxNb/sKA8zxPnxT+EObCmsjNZHFC2HJDWo1K3cLx2QMEJcE2QbT9SPOsWLG7mxGEdUTiOFZLWYdmKAoTtfZ+qoL8jLv7TS/HRKZ0ygJ6/snpqwgECI4t5QPGk/mHjvWkeU5bFDcLOdKkPUaA0Lu8ubnwnUovO8/6+3GIICRJ7v2qvSi6sNoOdaKikgJgfnbtJBPCh03/q21jNlThFRRk4bzM9pu9ydiTxiPmFy4VBDdqE8jXtDKRzuYkE1vxatHvMsp7nIIMHhX44WXsH9oautMrcEU0aPUj7WeosyS1/0NrdFnsStFcB6ACm/wz1ABRDjURgOhFDIhLxSH8RyflTs3KDcCnwYt1wjXddSvJIzv7fcXyhWMbmV1nte2eN5YvgRi9lp1PLM1Y/gnJfR2PAz6K+Acp5chSbQnfQ+uo43j2soo+nD6x6SghtXM5Ya4zK538VB12MSzp6FZRFbsxJUNoFQlkJiK8VIRwxlYqptHhtX2rTjovOIv0RqF08iju/jNDATAuvoKhk6k0/u1VTDU76+D3lHO1Bgw04p0a2B9YJYY3wHQuFWFlCXpLZcYCamclbupHsKp/q4mgL1uCSdqgtxgElOcDjjwHAuCrW9VySB6G8vu2Z1TQ6zxvKS9t2JIj8DXt8C1NbfamM', '-----BEGIN CERTIFICATE-----\nMIIDPTCCAiWgAwIBAgIUHE8Qk/e1SYIvdwd/yW840JWZ/OQwDQYJKoZIhvcNAQEL\nBQAwHjEcMBoGA1UEAwwTdXBsb2FkLXRlc3QuaW52YWxpZDAeFw0yNjEwMTQxMTU2\nNTJaFw0yNzEwMTQxMTU2NTJaMB4xHDAaBgNVBAMME3VwbG9hZC10ZXN0LmludmFs\naWQwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQD2e6g8re9JaGGi8koz\nplQJKKQI44fwa2LQYn9pVJS2DR9KGgxUg2Kjw6Uk8mSloR2OOqHOqg5tp5ngzB8T\nmedN3Yb+3MXKIzV+I3wxlqfFMvAdcNlCNuA9BpV3uFRmA9+HVn3M35IxkiTRhKLc\nNFcGlGb+wErWSG2QFQXyb+gwhaTeYOnxvo3p2FpKPCW4aeSenLBjSIlKmseRR7HQ\nxj2UQwWTZOI38lYwNBgvKEKCyEJqlIJntqOYa5C9wxsJdCHReW9xZIRiJ1CC7EZY\nUdDkKdjjKl2r/wcXo8nRzPcanlNlpdeppVoBXug7/erC9+nuHyZfHy/j/zQQcPkn\ncEcDAgMBAAGjczBxMB0GA1UdDgQWBBSEUpb4z8Idcqhv8/m15/fzDLxGhDAfBgNV\nHSMEGDAWgBSEUpb4z8Idcqhv8/m15/fzDLxGhDAPBgNVHRMBAf8EBTADAQH/MB4G\nA1UdEQQXMBWCE3VwbG9hZC10ZXN0LmludmFsaWQwDQYJKoZIhvcNAQELBQADggEB\nACCUT1XoJgYtAHmy/N4csXPitQyPnF4qxg+s/64b70kvDBlklE4LeaMdxmP91LU7\n61XGogVDWKfxgjk300jtWoMjzByb3D9nqC5bhVZE9YbmhkmqRtI1nZsbpANBqu9P\nEXN5OQ1Rt0FV6ZR8JZJNMAs77sfOlMH477oQc/gYLzdxI82jv0u0DpJ47GmROPji\nsJ2aTThK/x5mGFcRkGhrcvRJjGh43awmHTyueJm0YgIZDH8/jt1873NIp2XDtEzx\nCbX/hw80NNuTJbjS4/t0+nrn+m1rnWTvcMOLc8gL/hacvgBMnPta3UTnVOGAhJCK\nD7CDODpHKM/ZuV+gqFLL3K0=\n-----END CERTIFICATE-----\n', '2026-10-14 11:58:15');
INSERT INTO `certificate_versions` (`id`, `certificate_id`, `version_number`, `encrypted_private_key`, `certificate_pem`, `created_at`) VALUES (2, 1, 2, 'jrE9m8SUAx6pKFVnQRa2tKGJkwJ0DGgYVDI2z/UdHxhV7smai4cs1LbzRExb4d65s3na9CzTJOXQuYy9Nte4CF7YoL4gjp4ajMNh0dHLEZex9c9AZN26bcrqijddFVprXx40hnwzedeyziQ+g5Q2TLoyjSZdAgVRhy/E2Hf0zlPV2yJTIfegWvs1Kpc0oJJmM1QwejI+ojrQUJPP6ow5D9j86kPqt25yzVBnbscN+qeDl1qytDXe/JQhNEei2JZcIRpQCtoQQ2Uhr6h6uL3P0g90crDpsDhqOUa3OTKwATwBc6rOwqrKJAUky2Q0tcc0TLn6bu3ccXu7wiR4Rq3XxURS2UA3knQKv7CCu3SOFYWZY+q2fKiCgEqksbnHHWFzobvoF2JNIZ9XltVxl/7inN4QtXRvn9oO5z89W0lCECI/HHSmFyy7mg+6PdsDjmDJrw1wLEmaTKWMQXJwy+AxBhEfTtoNwLm36S5nnj86HFjwcYJZ3Q5DI6bxcLV1p48XRyccukjEQa1He9GlhqbT5Zms6FpmS/1LVjeucOZqB03/Gwb46MOwri/p4E2JzUq14Op+ANorf8LoOZWLVNnGM9jR8+u44hyEKIDUq90QUsaF4UWia/ReHCvdOXbckId+Z6Hvops/8wjwmuUlLxdD1VNmo/etse1le7IwX5pgdgfoGniokFxX2NAehGcTcFhgQI/ZhwLQG1Z5X/wdZHnnrHlBzKnMFqLMgzUyoFqraOv/0k9GbNnQtyK3szoB1JZNB6ej36JS3k5gBIOFYqkJlISHHwI4BMY77vfaJcxbALXluRt+eIM9Ohsna0sgQChrC1S0MouNQUXzKbOYrF62mGAxD5ePQXKZ/94xbkeOqb8EdIjIJh9wavK2jDX6X8T56ZXDA4zj1KWLqIv5f6NjyhIL0igUNod60h8JqyTWEwvQ9bldAxji4qo5b58CgSTvcuHT/CJU8Vf0Wx+he3csxBhOpx6dgKdA7GqTs3txPZn6rKLt3Qzj038OGnjOKw8LSiwBVFjb5Lomz8kZxB0LUmt+V8tpFjRnuPf5gTH/6emjEiVecaiACCcvYFCdlBi9t1lTtL+xN7VqqCgvK1+QYFPrbVzVN4Oqa9FCeE2xeXubdadSjnMSPGxHnqhhJSgBJgzFWgKbAZR97PF0kbQjUWT9h0pftG93asDHUVZfRBTdO5B5cRcGOOFD0GPSt/4HxM6Qe7J5rGyjIYn9rgEwgqeXFeT5uM0a3a6ocIzlWxb/cqIBd+1KscuUO63+lBH8wkveOG+U25ZLOsdtedPDzA4zqcomxiPVJE7uAvNDxnp9DfPuyezgVzc4/UYKSZ0pcSH9W57mCijKv2KCwaou+lKMtjLc2iJaVujzS/NOTXtIqT51sRliA1vfwdg97l4qJUDaDMKY1kpEg0AEFyFo/wJ9k5ETsK5LgU5OM/rrcHE4dmMZQymRit0ivGcrftqR+YmGGx/MjRdzU8u9RE4zRxeBhwFn6nuzm3YvRUWRG97QkC2Ws19A8cQ+pT02FtvUJjFeu8dTTaAwxprFtF6b3TP+mHLUyZeD9WSnNCLjsqVGGz3Q+YR1C+pXR3GnT36XYveR3R8oYprYAf0QaI64M6GWIoNhaC4//NWDSS/4xa6r1IzKFqZxU62/XLe3', '-----BEGIN CERTIFICATE-----\nMIIDPTCCAiWgAwIBAgIUHE8Qk/e1SYIvdwd/yW840JWZ/OQwDQYJKoZIhvcNAQEL\nBQAwHjEcMBoGA1UEAwwTdXBsb2FkLXRlc3QuaW52YWxpZDAeFw0yNjEwMTQxMTU2\nNTJaFw0yNzEwMTQxMTU2NTJaMB4xHDAaBgNVBAMME3VwbG9hZC10ZXN0LmludmFs\naWQwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQD2e6g8re9JaGGi8koz\nplQJKKQI44fwa2LQYn9pVJS2DR9KGgxUg2Kjw6Uk8mSloR2OOqHOqg5tp5ngzB8T\nmedN3Yb+3MXKIzV+I3wxlqfFMvAdcNlCNuA9BpV3uFRmA9+HVn3M35IxkiTRhKLc\nNFcGlGb+wErWSG2QFQXyb+gwhaTeYOnxvo3p2FpKPCW4aeSenLBjSIlKmseRR7HQ\nxj2UQwWTZOI38lYwNBgvKEKCyEJqlIJntqOYa5C9wxsJdCHReW9xZIRiJ1CC7EZY\nUdDkKdjjKl2r/wcXo8nRzPcanlNlpdeppVoBXug7/erC9+nuHyZfHy/j/zQQcPkn\ncEcDAgMBAAGjczBxMB0GA1UdDgQWBBSEUpb4z8Idcqhv8/m15/fzDLxGhDAfBgNV\nHSMEGDAWgBSEUpb4z8Idcqhv8/m15/fzDLxGhDAPBgNVHRMBAf8EBTADAQH/MB4G\nA1UdEQQXMBWCE3VwbG9hZC10ZXN0LmludmFsaWQwDQYJKoZIhvcNAQELBQADggEB\nACCUT1XoJgYtAHmy/N4csXPitQyPnF4qxg+s/64b70kvDBlklE4LeaMdxmP91LU7\n61XGogVDWKfxgjk300jtWoMjzByb3D9nqC5bhVZE9YbmhkmqRtI1nZsbpANBqu9P\nEXN5OQ1Rt0FV6ZR8JZJNMAs77sfOlMH477oQc/gYLzdxI82jv0u0DpJ47GmROPji\nsJ2aTThK/x5mGFcRkGhrcvRJjGh43awmHTyueJm0YgIZDH8/jt1873NIp2XDtEzx\nCbX/hw80NNuTJbjS4/t0+nrn+m1rnWTvcMOLc8gL/hacvgBMnPta3UTnVOGAhJCK\nD7CDODpHKM/ZuV+gqFLL3K0=\n-----END CERTIFICATE-----\n', '2026-10-14 11:58:31');
COMMIT;

-- ----------------------------
-- Table structure for certificates
-- ----------------------------
DROP TABLE IF EXISTS `certificates`;
CREATE TABLE `certificates` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `domain_id` bigint DEFAULT NULL,
  `common_name` varchar(253) COLLATE utf8mb4_general_ci NOT NULL,
  `domains` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(24) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PENDING',
  `valid_from` timestamp NULL DEFAULT NULL,
  `valid_until` timestamp NULL DEFAULT NULL,
  `issuer` varchar(80) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `challenge_type` varchar(24) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `auto_renew` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_cert_domain` (`domain_id`),
  CONSTRAINT `fk_cert_domain` FOREIGN KEY (`domain_id`) REFERENCES `domains` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- Records of certificates
-- ----------------------------
BEGIN;
INSERT INTO `certificates` (`id`, `domain_id`, `common_name`, `domains`, `status`, `valid_from`, `valid_until`, `issuer`, `challenge_type`, `auto_renew`, `created_at`) VALUES (1, NULL, 'upload-test.invalid', 'upload-test.invalid', 'ACTIVE', '2026-10-14 11:56:52', '2027-10-14 11:56:52', 'upload-test.invalid', NULL, 0, '2026-10-14 11:58:14');
COMMIT;

-- ----------------------------
-- Table structure for domains
-- ----------------------------
DROP TABLE IF EXISTS `domains`;
CREATE TABLE `domains` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(253) COLLATE utf8mb4_general_ci NOT NULL,
  `registered_at` date DEFAULT NULL,
  `expires_at` date DEFAULT NULL,
  `registrar` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACTIVE',
  `name_servers` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `lookup_status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PENDING',
  `last_refreshed_at` timestamp NULL DEFAULT NULL,
  `alert_recipients` varchar(1200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `dns_records_json` longtext COLLATE utf8mb4_general_ci NOT NULL,
  `lookup_message` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- Records of domains
-- ----------------------------
BEGIN;
INSERT INTO `domains` (`id`, `name`, `registered_at`, `expires_at`, `registrar`, `status`, `name_servers`, `lookup_status`, `last_refreshed_at`, `alert_recipients`, `created_at`, `dns_records_json`, `lookup_message`) VALUES (1, 'catpanel.cn', '2021-06-22', '2031-06-22', '阿里云计算有限公司（万网）', 'ACTIVE', 'dns23.hichina.com,dns24.hichina.com', 'SUCCESS', '2026-10-14 12:01:39', NULL, '2026-10-14 07:16:26', '[{\"value\":\"8.140.241.220\",\"type\":\"A\"},{\"value\":\"dns24.hichina.com.\",\"type\":\"NS\"},{\"value\":\"dns23.hichina.com.\",\"type\":\"NS\"},{\"value\":\"globalsign-domain-verification=bCz7Nohl0mtziwNpLB36GxAnE7f-gNBdpYk7qZCCr7\",\"type\":\"TXT\"},{\"value\":\"globalsign-domain-verification=WXiWICOqWeu7uaoY-KO4tVRU0U7qN187Tuvvs3MXhi\",\"type\":\"TXT\"},{\"value\":\"globalsign-domain-verification=ubtbJ2oS1i9keMZDiWqLiOx1VYDh70LQBLoel7UsOx\",\"type\":\"TXT\"},{\"value\":\"dns23.hichina.com. hostmaster.hichina.com. 2026082617 3600 1200 86400 600\",\"type\":\"SOA\"}]', NULL);
INSERT INTO `domains` (`id`, `name`, `registered_at`, `expires_at`, `registrar`, `status`, `name_servers`, `lookup_status`, `last_refreshed_at`, `alert_recipients`, `created_at`, `dns_records_json`, `lookup_message`) VALUES (5, 'netbors.com', '2014-05-03', '2027-05-03', 'Go China Domains, LLC', 'ACTIVE', 'ns-1077.awsdns-06.org,ns-1801.awsdns-33.co.uk,ns-454.awsdns-56.com,ns-823.awsdns-38.net', 'SUCCESS', '2026-10-14 14:30:02', NULL, '2026-10-14 14:29:49', '[{\"type\":\"A\",\"value\":\"54.187.240.34\"},{\"type\":\"MX\",\"value\":\"5 hzmx01.mxmail.netease.com.\"},{\"type\":\"MX\",\"value\":\"10 hzmx02.mxmail.netease.com.\"},{\"type\":\"NS\",\"value\":\"ns-1801.awsdns-33.co.uk.\"},{\"type\":\"NS\",\"value\":\"ns-454.awsdns-56.com.\"},{\"type\":\"NS\",\"value\":\"ns-1077.awsdns-06.org.\"},{\"type\":\"NS\",\"value\":\"ns-823.awsdns-38.net.\"},{\"type\":\"TXT\",\"value\":\"\\\"v=spf1 include:spf.163.com -all\\\"\"},{\"type\":\"SOA\",\"value\":\"ns-1077.awsdns-06.org. awsdns-hostmaster.amazon.com. 1 7200 900 1209600 86400\"}]', NULL);
COMMIT;

-- ----------------------------
-- Table structure for flyway_schema_history
-- ----------------------------
DROP TABLE IF EXISTS `flyway_schema_history`;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `description` varchar(200) COLLATE utf8mb4_general_ci NOT NULL,
  `type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `script` varchar(1000) COLLATE utf8mb4_general_ci NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- Records of flyway_schema_history
-- ----------------------------
BEGIN;
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (1, '1', 'initial schema', 'SQL', 'V1__initial_schema.sql', -167149120, 'root', '2026-10-14 07:15:18', 75, 1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (2, '2', 'domain lookup details', 'SQL', 'V2__domain_lookup_details.sql', -1348357062, 'root', '2026-10-14 07:15:18', 52, 1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (3, '3', 'audit details text', 'SQL', 'V3__audit_details_text.sql', -566108830, 'root', '2026-10-14 07:15:18', 29, 1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (4, '4', 'system settings', 'SQL', 'V4__system_settings.sql', 1399140259, 'root', '2026-10-14 14:10:14', 37, 1);
COMMIT;

-- ----------------------------
-- Table structure for system_settings
-- ----------------------------
DROP TABLE IF EXISTS `system_settings`;
CREATE TABLE `system_settings` (
  `setting_key` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `setting_value` text COLLATE utf8mb4_general_ci NOT NULL,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- Records of system_settings
-- ----------------------------
BEGIN;
INSERT INTO `system_settings` (`setting_key`, `setting_value`, `updated_at`) VALUES ('certificate.encryption-key', 'CzoeP+3c7HV8phx33d0jTYEVOpCfjR8TshexSwOGT5V6rfK9nVYDRII1JfQqtoWlYdDllhx0ecnyFPiuIdDp62HVbUocV4gM', '2026-10-14 14:10:16');
INSERT INTO `system_settings` (`setting_key`, `setting_value`, `updated_at`) VALUES ('mail.auth', 'true', '2026-10-14 14:10:16');
INSERT INTO `system_settings` (`setting_key`, `setting_value`, `updated_at`) VALUES ('mail.from', 'vip12233@foxmail.com', '2026-10-14 14:10:16');
INSERT INTO `system_settings` (`setting_key`, `setting_value`, `updated_at`) VALUES ('mail.host', 'smtp.qq.com', '2026-10-14 14:10:16');
INSERT INTO `system_settings` (`setting_key`, `setting_value`, `updated_at`) VALUES ('mail.password', 'Ut4iouAXy81ngSL1IY8ueeBU7Uyu4Ib7xd4SepAWBSQTpoKz/L/12buKS3k=', '2026-10-14 14:10:16');
INSERT INTO `system_settings` (`setting_key`, `setting_value`, `updated_at`) VALUES ('mail.port', '587', '2026-10-14 14:10:16');
INSERT INTO `system_settings` (`setting_key`, `setting_value`, `updated_at`) VALUES ('mail.recipients', '', '2026-10-14 14:10:16');
INSERT INTO `system_settings` (`setting_key`, `setting_value`, `updated_at`) VALUES ('mail.starttls', 'true', '2026-10-14 14:10:16');
INSERT INTO `system_settings` (`setting_key`, `setting_value`, `updated_at`) VALUES ('mail.username', 'vip12233@foxmail.com', '2026-10-14 14:10:16');
COMMIT;

-- ----------------------------
-- Table structure for users
-- ----------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `email` varchar(254) COLLATE utf8mb4_general_ci NOT NULL,
  `password_hash` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `display_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL,
  `role` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ----------------------------
-- Records of users
-- ----------------------------
BEGIN;
INSERT INTO `users` (`id`, `email`, `password_hash`, `display_name`, `role`, `enabled`, `created_at`) VALUES (1, 'admin', '$2a$10$vogio9NCFWyCQTUw37GTi.kW541.Rjr5ejE9jG9IVwulRyNi5EMlC', '管理员', 'ADMIN', 1, '2026-10-14 07:15:21');
INSERT INTO `users` (`id`, `email`, `password_hash`, `display_name`, `role`, `enabled`, `created_at`) VALUES (2, 'operator', '$2a$10$8mVtIMHLwic5MY8w2pIobOsC1x8PONO6hE3S8tw3SKu3/uW3zkZE2', '运维', 'OPERATOR', 1, '2026-10-14 07:15:21');
INSERT INTO `users` (`id`, `email`, `password_hash`, `display_name`, `role`, `enabled`, `created_at`) VALUES (3, 'viewer', '$2a$10$D.Pj.Lg4PUi6x4P5UC7r5e8.XjMTS/zKuXf96cdqcvOeqx/2ZgpaC', '只读用户', 'VIEWER', 1, '2026-10-14 07:15:21');
COMMIT;

SET FOREIGN_KEY_CHECKS = 1;
