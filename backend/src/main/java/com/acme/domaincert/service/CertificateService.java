package com.acme.domaincert.service;

import com.acme.domaincert.domain.CertificateAsset;
import com.acme.domaincert.domain.CertificateVersion;
import com.acme.domaincert.repository.CertificateRepository;
import com.acme.domaincert.repository.CertificateVersionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 处理证书 PEM 导入、密钥验证、加密保存和授权下载。 */
@Service
public class CertificateService {

    private static final int MAX_UPLOAD_BYTES = 1_048_576;

    private final CertificateRepository certificates;
    private final CertificateVersionRepository versions;
    private final AuditService auditService;
    private final SystemSettingsService systemSettings;

    public CertificateService(CertificateRepository certificates,
                              CertificateVersionRepository versions,
                              AuditService auditService, SystemSettingsService systemSettings) {
        this.certificates = certificates;
        this.versions = versions;
        this.auditService = auditService;
        this.systemSettings = systemSettings;
    }

    /**
     * 导入一份 PEM 证书与 PKCS#8 私钥。
     * 上传文件应包含证书链与以 BEGIN PRIVATE KEY 标记的未加密私钥。
     */
    @Transactional
    public CertificateAsset upload(MultipartFile file, MultipartFile privateKeyFile,
                                   org.springframework.security.core.Authentication actor) {
        try {
            if (file == null || file.isEmpty() || file.getSize() > MAX_UPLOAD_BYTES
                    || (privateKeyFile != null && privateKeyFile.getSize() > MAX_UPLOAD_BYTES)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件为空或超过 1 MB 限制");
            }
            String pemBundle = normalizePem(new String(file.getBytes(), StandardCharsets.UTF_8));
            // 私钥可与证书链放在同一个 PEM 中，也可单独选择常见的 privkey.pem 文件。
            if (privateKeyFile != null && !privateKeyFile.isEmpty()) {
                pemBundle += "\n" + normalizePem(new String(privateKeyFile.getBytes(), StandardCharsets.UTF_8));
            }
            String certificatesPem = extractCertificatePem(pemBundle);
            X509Certificate leaf = parseLeafCertificate(certificatesPem);
            PrivateKey privateKey = parsePrivateKey(pemBundle, leaf);
            verifyKeyPair(privateKey, leaf);

            Instant now = Instant.now();
            if (leaf.getNotAfter().toInstant().isBefore(now)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能导入已过期的证书");
            }

            String commonName = commonName(leaf.getSubjectX500Principal().getName());
            String domains = String.join(",", certificateNames(leaf, commonName));
            String issuer = commonName(leaf.getIssuerX500Principal().getName());
            CertificateAsset asset = certificates.findFirstByCommonNameIgnoreCase(commonName)
                    .orElseGet(() -> new CertificateAsset(commonName, domains,
                            leaf.getNotBefore().toInstant(), leaf.getNotAfter().toInstant(), issuer));
            asset.replaceMetadata(domains, leaf.getNotBefore().toInstant(), leaf.getNotAfter().toInstant(), issuer);
            asset = certificates.save(asset);

            int nextVersion = versions.findTopByCertificateIdOrderByVersionNumberDesc(asset.getId())
                    .map(version -> version.getVersionNumber() + 1)
                    .orElse(1);
            versions.save(new CertificateVersion(asset, nextVersion, encrypt(privateKey.getEncoded()), certificatesPem));
            auditService.record(actor, "CERTIFICATE_VERSION_UPLOADED", "CERTIFICATE", asset.getId(), commonName);
            return asset;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            String reason = exception instanceof IllegalArgumentException && exception.getMessage() != null
                    ? exception.getMessage()
                    : "请提供完整的 X.509 证书链和匹配的未加密 PEM 私钥（PKCS#8 或 RSA PKCS#1）";
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "证书上传失败：" + reason, exception);
        }
    }

    /** 规范化 Windows 换行和 UTF-8 BOM，避免编辑器生成的 BOM 导致 PEM 解析失败。 */
    private String normalizePem(String content) {
        return content.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    /** 保存 ACME 返回的证书链和本地生成的私钥，复用上传流程的元数据与加密策略。 */
    @Transactional
    public CertificateAsset saveIssued(String certificatesPem, PrivateKey privateKey,
                                       org.springframework.security.core.Authentication actor) {
        try {
            X509Certificate leaf = parseLeafCertificate(certificatesPem);
            verifyKeyPair(privateKey, leaf);
            String commonName = commonName(leaf.getSubjectX500Principal().getName());
            String domains = String.join(",", certificateNames(leaf, commonName));
            String issuer = commonName(leaf.getIssuerX500Principal().getName());
            CertificateAsset asset = certificates.findFirstByCommonNameIgnoreCase(commonName)
                    .orElseGet(() -> new CertificateAsset(commonName, domains,
                            leaf.getNotBefore().toInstant(), leaf.getNotAfter().toInstant(), issuer));
            asset.replaceMetadata(domains, leaf.getNotBefore().toInstant(), leaf.getNotAfter().toInstant(), issuer);
            asset = certificates.save(asset);
            int nextVersion = versions.findTopByCertificateIdOrderByVersionNumberDesc(asset.getId())
                    .map(version -> version.getVersionNumber() + 1).orElse(1);
            versions.save(new CertificateVersion(asset, nextVersion, encrypt(privateKey.getEncoded()), certificatesPem));
            auditService.record(actor, "CERTIFICATE_ISSUED", "CERTIFICATE", asset.getId(), commonName);
            return asset;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "证书签发结果无法安全保存", exception);
        }
    }

    /** 只返回证书公有信息的版本记录，不向 API 响应泄露加密私钥字段。 */
    @Transactional(readOnly = true)
    public String downloadPem(Long certificateId) {
        CertificateAsset asset = certificates.findById(certificateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到该证书"));
        CertificateVersion version = versions.findTopByCertificateIdOrderByVersionNumberDesc(certificateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "该证书没有可下载版本"));
        String privateKey = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(decrypt(version.getEncryptedPrivateKey()));
        return "-----BEGIN PRIVATE KEY-----\n" + privateKey + "\n-----END PRIVATE KEY-----\n" + version.getCertificatePem();
    }

    /** 允许运维人员维护自动续期偏好；实际 ACME 续期由后续 CA 接入模块执行。 */
    @Transactional
    public CertificateAsset setAutoRenew(Long certificateId, boolean enabled,
                                         org.springframework.security.core.Authentication actor) {
        CertificateAsset asset = certificates.findById(certificateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到该证书"));
        asset.setAutoRenew(enabled);
        auditService.record(actor, "CERTIFICATE_RENEWAL_SETTING_CHANGED", "CERTIFICATE", asset.getId(),
                enabled ? "已开启自动续期" : "已关闭自动续期");
        return certificates.save(asset);
    }

    /** 删除证书资产；其历史版本由外键级联清理，并写入审计记录。 */
    @Transactional
    public void delete(Long certificateId, org.springframework.security.core.Authentication actor) {
        CertificateAsset asset = certificates.findById(certificateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到该证书"));
        auditService.record(actor, "CERTIFICATE_DELETED", "CERTIFICATE", asset.getId(), asset.getCommonName());
        certificates.delete(asset);
    }

    /** 从 PEM bundle 提取第一个 X.509 证书作为叶证书。 */
    private X509Certificate parseLeafCertificate(String pemBundle) throws Exception {
        byte[] bytes = pemBundle.getBytes(StandardCharsets.US_ASCII);
        Collection<? extends java.security.cert.Certificate> parsed = CertificateFactory.getInstance("X.509")
                .generateCertificates(new ByteArrayInputStream(bytes));
        return parsed.stream()
                .filter(X509Certificate.class::isInstance)
                .map(X509Certificate.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("缺少 X.509 证书"));
    }

    /** 仅提取证书块存入证书列，确保上传文件内的明文私钥不会进入数据库。 */
    private String extractCertificatePem(String pemBundle) {
        Pattern pattern = Pattern.compile("-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----",
                Pattern.DOTALL);
        Matcher matcher = pattern.matcher(pemBundle);
        StringBuilder certificatesPem = new StringBuilder();
        while (matcher.find()) certificatesPem.append(matcher.group()).append('\n');
        if (certificatesPem.isEmpty()) {
            if (pemBundle.contains("-----BEGIN CERTIFICATE-----")) {
                throw new IllegalArgumentException("证书 PEM 缺少 -----END CERTIFICATE----- 结束标记");
            }
            throw new IllegalArgumentException("文件中没有 X.509 PEM 证书块");
        }
        return certificatesPem.toString();
    }

    /** 读取 PKCS#8 PEM 私钥，并按照证书公钥算法选择相应的 KeyFactory。 */
    private PrivateKey parsePrivateKey(String pemBundle, X509Certificate certificate) throws Exception {
        String marker = "-----BEGIN PRIVATE KEY-----";
        String endMarker = "-----END PRIVATE KEY-----";
        boolean rsaPkcs1 = false;
        int start = pemBundle.indexOf(marker);
        int end = pemBundle.indexOf(endMarker);
        if (start < 0 || end < start) {
            marker = "-----BEGIN RSA PRIVATE KEY-----";
            endMarker = "-----END RSA PRIVATE KEY-----";
            start = pemBundle.indexOf(marker);
            end = pemBundle.indexOf(endMarker);
            rsaPkcs1 = start >= 0 && end >= start;
        }
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("缺少完整的未加密 PKCS#8 或 RSA PKCS#1 私钥 PEM 块");
        }
        String encoded = pemBundle.substring(start + marker.length(), end).replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(encoded);
        String algorithm = certificate.getPublicKey().getAlgorithm();
        // Java KeyFactory 只接收 PKCS#8；将传统 RSA PKCS#1 DER 包装为 PKCS#8 后再解析。
        if (rsaPkcs1 && "RSA".equals(algorithm)) keyBytes = wrapRsaPkcs1AsPkcs8(keyBytes);
        return KeyFactory.getInstance(algorithm).generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    /** 将 PKCS#1 的 RSA 私钥 DER 包装成标准 PKCS#8 PrivateKeyInfo DER。 */
    private byte[] wrapRsaPkcs1AsPkcs8(byte[] pkcs1) {
        byte[] algorithmIdentifier = new byte[] {
                0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86,
                (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00
        };
        byte[] version = new byte[] { 0x02, 0x01, 0x00 };
        byte[] privateKeyOctets = derValue(0x04, pkcs1);
        byte[] content = new byte[version.length + algorithmIdentifier.length + privateKeyOctets.length];
        System.arraycopy(version, 0, content, 0, version.length);
        System.arraycopy(algorithmIdentifier, 0, content, version.length, algorithmIdentifier.length);
        System.arraycopy(privateKeyOctets, 0, content, version.length + algorithmIdentifier.length,
                privateKeyOctets.length);
        return derValue(0x30, content);
    }

    /** 编码带 DER 长度字段的 TLV，支持 PEM 私钥常见的长数据块。 */
    private byte[] derValue(int tag, byte[] value) {
        int lengthBytes = value.length < 128 ? 1 : 1 + (Integer.SIZE - Integer.numberOfLeadingZeros(value.length) + 7) / 8;
        byte[] result = new byte[1 + lengthBytes + value.length];
        result[0] = (byte) tag;
        if (value.length < 128) {
            result[1] = (byte) value.length;
        } else {
            int count = lengthBytes - 1;
            result[1] = (byte) (0x80 | count);
            for (int index = 0; index < count; index++) {
                result[2 + index] = (byte) (value.length >>> (8 * (count - index - 1)));
            }
        }
        System.arraycopy(value, 0, result, 1 + lengthBytes, value.length);
        return result;
    }

    /** 用随机挑战签名并验证，确保上传的证书与私钥确实成对。 */
    private void verifyKeyPair(PrivateKey privateKey, X509Certificate certificate) throws Exception {
        String keyAlgorithm = certificate.getPublicKey().getAlgorithm();
        String signatureAlgorithm = switch (keyAlgorithm) {
            case "RSA" -> "SHA256withRSA";
            case "EC" -> "SHA256withECDSA";
            case "Ed25519" -> "Ed25519";
            default -> throw new IllegalArgumentException("不支持的私钥算法");
        };
        byte[] challenge = "domain-cert-key-match".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance(signatureAlgorithm);
        signer.initSign(privateKey);
        signer.update(challenge);
        byte[] proof = signer.sign();

        Signature verifier = Signature.getInstance(signatureAlgorithm);
        verifier.initVerify(certificate.getPublicKey());
        verifier.update(challenge);
        if (!verifier.verify(proof)) throw new IllegalArgumentException("证书和私钥不匹配");
    }

    /** 使用系统设置中的当前 AES-256 数据密钥加密证书私钥。 */
    private String encrypt(byte[] clearText) throws Exception {
        return AesGcmCrypto.encrypt(clearText, encryptionKey());
    }

    /** 使用 AES-GCM 解密密钥材料；认证标签错误时拒绝下载。 */
    private byte[] decrypt(String encoded) {
        try {
            return AesGcmCrypto.decrypt(encoded, encryptionKey());
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "私钥解密失败，请检查系统密钥配置", exception);
        }
    }

    private byte[] encryptionKey() {
        byte[] key = Base64.getDecoder().decode(systemSettings.certificateEncryptionKey());
        if (key.length != 32) throw new IllegalStateException("app.certificate.encryption-key 必须解码为 32 字节");
        return key;
    }

    /** 提取证书主题备用名称，并始终包含证书 CN。 */
    private Set<String> certificateNames(X509Certificate certificate, String commonName) throws Exception {
        Set<String> names = new LinkedHashSet<>();
        names.add(commonName);
        Collection<List<?>> subjectAltNames = certificate.getSubjectAlternativeNames();
        if (subjectAltNames != null) {
            for (List<?> entry : subjectAltNames) {
                if (entry.size() >= 2 && entry.get(0) instanceof Integer type && type == 2) {
                    names.add(String.valueOf(entry.get(1)));
                }
            }
        }
        return names;
    }

    /** 从 X.500 名称中取 CN；不存在 CN 时退回显示完整名称。 */
    private String commonName(String distinguishedName) throws Exception {
        for (Rdn rdn : new LdapName(distinguishedName).getRdns()) {
            if ("CN".equalsIgnoreCase(rdn.getType())) return String.valueOf(rdn.getValue());
        }
        return distinguishedName;
    }
}
