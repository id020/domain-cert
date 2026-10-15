package com.acme.domaincert.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SNIHostName;
import javax.security.auth.x500.X500Principal;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** 通过 HTTPS 握手读取域名当前部署的公开 TLS 证书元数据，不保存、不请求私钥。 */
@Service
public class CertificateInspectionService {

    private static final int TLS_PORT = 443;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 7000;
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "(?i)^(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$");

    /** 查询结果只包含证书链的公有信息，可直接作为 JSON 返回。 */
    public record Inspection(String domain, String commonName, List<String> domains, String issuer,
                             String serialNumber, String fingerprintSha256, String signatureAlgorithm,
                             String publicKeyAlgorithm, Integer publicKeyBits, Instant validFrom,
                             Instant validUntil, String tlsProtocol, List<ChainEntry> chain) { }

    /** 证书链中每一级的主题、签发者和有效期。 */
    public record ChainEntry(String subject, String issuer, Instant validFrom, Instant validUntil) { }

    /** 校验域名、拒绝内网地址后连接固定 443 端口并读取对端证书链。 */
    public Inspection inspect(String input) {
        String host = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        if (!DOMAIN_PATTERN.matcher(host).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入有效的完整域名，例如 baidu.com");
        }

        try {
            // 先解析并检查全部地址，再直连已检查的 IP，避免把请求转发到本机或内网服务。
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) throw new IllegalArgumentException("域名没有可用地址");
            for (InetAddress address : addresses) {
                if (!isPublicAddress(address)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不允许查询内网或保留地址");
                }
            }

            try (Socket plain = new Socket()) {
                plain.connect(new InetSocketAddress(addresses[0], TLS_PORT), CONNECT_TIMEOUT_MS);
                plain.setSoTimeout(READ_TIMEOUT_MS);
                SSLSocketFactory factory = SSLContext.getDefault().getSocketFactory();
                try (SSLSocket tls = (SSLSocket) factory.createSocket(plain, host, TLS_PORT, true)) {
                    // 开启标准主机名校验和 SNI，确保证书确实适用于用户输入的域名。
                    SSLParameters parameters = tls.getSSLParameters();
                    parameters.setEndpointIdentificationAlgorithm("HTTPS");
                    parameters.setServerNames(List.of(new SNIHostName(host)));
                    tls.setSSLParameters(parameters);
                    tls.startHandshake();
                    Certificate[] certificates = tls.getSession().getPeerCertificates();
                    if (certificates.length == 0 || !(certificates[0] instanceof X509Certificate leaf)) {
                        throw new IllegalArgumentException("目标服务器没有提供 X.509 证书");
                    }
                    return toInspection(host, leaf, certificates, tls.getSession().getProtocol());
                }
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "无法读取该域名的 HTTPS 证书，请检查 DNS、443 端口和证书主机名", exception);
        }
    }

    /** 提取叶证书、SAN、指纹、公钥参数以及握手返回的证书链。 */
    private Inspection toInspection(String host, X509Certificate leaf, Certificate[] certificates,
                                    String tlsProtocol) throws Exception {
        List<String> domains = new ArrayList<>();
        if (leaf.getSubjectAlternativeNames() != null) {
            for (List<?> entry : leaf.getSubjectAlternativeNames()) {
                // SAN 类型 2 为 DNS 名称，类型 7 为 IP 地址。
                if (entry.size() >= 2 && (Integer.valueOf(2).equals(entry.get(0))
                        || Integer.valueOf(7).equals(entry.get(0)))) {
                    domains.add(String.valueOf(entry.get(1)));
                }
            }
        }
        if (domains.isEmpty()) domains.add(host);

        PublicKey publicKey = leaf.getPublicKey();
        List<ChainEntry> chain = new ArrayList<>();
        for (Certificate certificate : certificates) {
            if (certificate instanceof X509Certificate x509) {
                chain.add(new ChainEntry(x509.getSubjectX500Principal().getName(),
                        x509.getIssuerX500Principal().getName(), x509.getNotBefore().toInstant(),
                        x509.getNotAfter().toInstant()));
            }
        }
        String fingerprint = HexFormat.ofDelimiter(":").withUpperCase()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(leaf.getEncoded()));
        return new Inspection(host, commonName(leaf.getSubjectX500Principal()), domains,
                commonName(leaf.getIssuerX500Principal()), leaf.getSerialNumber().toString(16).toUpperCase(),
                fingerprint, leaf.getSigAlgName(), publicKey.getAlgorithm(), publicKeyBits(publicKey),
                leaf.getNotBefore().toInstant(), leaf.getNotAfter().toInstant(), tlsProtocol, chain);
    }

    /** 阻断环回、私有、链路本地、组播及常见文档/基准保留网段。 */
    private boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            if (first == 0 || first == 10 || first == 127 || first >= 224) return false;
            if (first == 100 && second >= 64 && second <= 127) return false;
            if (first == 169 && second == 254) return false;
            if (first == 172 && second >= 16 && second <= 31) return false;
            if (first == 192 && (second == 0 || second == 168)) return false;
            if (first == 198 && (second == 18 || second == 19 || second == 51)) return false;
            if (first == 203 && second == 0 && (bytes[2] & 0xff) == 113) return false;
        } else if (address instanceof Inet6Address) {
            // fc00::/7 是 IPv6 唯一本地地址，不一定被 JDK 标记为 site-local。
            if ((bytes[0] & 0xfe) == 0xfc) return false;
        }
        return true;
    }

    /** 从 X.500 名称提取 CN；没有 CN 时保留完整主题，便于诊断。 */
    private String commonName(X500Principal principal) {
        try {
            for (javax.naming.ldap.Rdn rdn : new javax.naming.ldap.LdapName(principal.getName()).getRdns()) {
                if ("CN".equalsIgnoreCase(rdn.getType())) return String.valueOf(rdn.getValue());
            }
        } catch (Exception ignored) {
            // 无法解析主题时仍可显示原始 X.500 名称。
        }
        return principal.getName();
    }

    /** 返回 RSA/DSA 位数或 EC 曲线位数；未知算法留空而不猜测。 */
    private Integer publicKeyBits(PublicKey key) {
        if (key instanceof java.security.interfaces.RSAKey rsa) return rsa.getModulus().bitLength();
        if (key instanceof java.security.interfaces.DSAKey dsa && dsa.getParams() != null) {
            return dsa.getParams().getP().bitLength();
        }
        if (key instanceof java.security.interfaces.ECKey ec && ec.getParams() != null) {
            return ec.getParams().getCurve().getField().getFieldSize();
        }
        return null;
    }
}
