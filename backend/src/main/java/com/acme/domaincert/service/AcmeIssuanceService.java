package com.acme.domaincert.service;

import com.acme.domaincert.domain.CertificateAsset;
import com.acme.domaincert.repository.DomainRepository;
import org.shredzone.acme4j.Account;
import org.shredzone.acme4j.AccountBuilder;
import org.shredzone.acme4j.Authorization;
import org.shredzone.acme4j.Certificate;
import org.shredzone.acme4j.Login;
import org.shredzone.acme4j.Order;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.Status;
import org.shredzone.acme4j.challenge.Http01Challenge;
import org.shredzone.acme4j.util.CSRBuilder;
import org.shredzone.acme4j.util.KeyPairUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 通过 ACME HTTP-01 签发真实证书，并在签发期间公开响应验证令牌。 */
@Service
public class AcmeIssuanceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcmeIssuanceService.class);

    private final CertificateService certificateService;
    private final DomainRepository domains;
    private final String directoryUrl;
    private final String accountKeyPath;
    private final int challengeTimeoutSeconds;

    /** 挑战内容仅在本次签发过程中短暂保存在内存中。 */
    private final Map<String, String> challengeResponses = new ConcurrentHashMap<>();

    public AcmeIssuanceService(CertificateService certificateService, DomainRepository domains,
                               @Value("${app.acme.directory-url}") String directoryUrl,
                               @Value("${app.acme.account-key-path}") String accountKeyPath,
                               @Value("${app.acme.challenge-timeout-seconds:180}") int challengeTimeoutSeconds) {
        this.certificateService = certificateService;
        this.domains = domains;
        this.directoryUrl = directoryUrl;
        this.accountKeyPath = accountKeyPath;
        this.challengeTimeoutSeconds = challengeTimeoutSeconds;
    }

    /** 运维人员申请一张多域名证书；所有域名必须先登记到资产库。 */
    public synchronized CertificateAsset issue(List<String> requestedDomains, String contactEmail,
                                               org.springframework.security.core.Authentication actor) {
        List<String> names = normalizeAndValidate(requestedDomains);
        for (String name : names) {
            if (!domains.existsByNameIgnoreCase(name)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先在域名管理中登记域名：" + name);
            }
        }

        try {
            Session session = new Session(URI.create(directoryUrl));
            KeyPair accountKey = loadOrCreateAccountKey();
            Login login = new AccountBuilder().addEmail(contactEmail).agreeToTermsOfService()
                    .useKeyPair(accountKey).createLogin(session);
            Account account = login.getAccount();
            Order order = account.newOrder().domains(names).create();
            List<Http01Challenge> challenges = new ArrayList<>();

            // 逐个发布 HTTP-01 文件并触发 CA 验证，保留挑战映射直至 CA 完成检查。
            for (Authorization authorization : order.getAuthorizations()) {
                if (authorization.isWildcard()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "HTTP-01 不支持通配符证书");
                }
                Http01Challenge challenge = authorization.findChallenge(Http01Challenge.class);
                if (challenge == null) {
                    throw new IllegalStateException("CA 未提供 HTTP-01 验证方式");
                }
                challengeResponses.put(challenge.getToken(), challenge.getAuthorization());
                challenges.add(challenge);
                challenge.trigger();
            }

            try {
                for (Http01Challenge challenge : challenges) {
                    waitForChallenge(challenge);
                }
                KeyPair certificateKey = createCertificateKey();
                CSRBuilder csr = new CSRBuilder();
                csr.addDomains(names);
                csr.sign(certificateKey);
                order.execute(csr.getEncoded());
                waitForOrder(order);

                Certificate issued = order.getCertificate();
                StringWriter pem = new StringWriter();
                issued.writeCertificate(pem);
                return certificateService.saveIssued(pem.toString(), certificateKey.getPrivate(), actor);
            } finally {
                challenges.forEach(challenge -> challengeResponses.remove(challenge.getToken()));
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            // 日志保留 ACME CA 的 problem detail 和网络异常，便于定位 502 的实际环节。
            LOGGER.error("ACME 签发失败，域名={}，目录={}", names, directoryUrl, exception);
            String detail = rootCauseMessage(exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "ACME 签发失败：" + detail + "。请确认域名已解析到本服务器，且公网 80 端口可访问。",
                    exception);
        }
    }

    /** 从异常链提取有限长度的根因文本，供授权用户查看 CA 返回的可诊断原因。 */
    private String rootCauseMessage(Throwable exception) {
        Throwable root = exception;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String message = root.getMessage();
        if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
        message = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return message.length() > 300 ? message.substring(0, 300) : message;
    }

    /** Nginx 将公开的 HTTP-01 路径转发至此，找不到的令牌返回 404。 */
    public String challengeResponse(String token) {
        String response = challengeResponses.get(token);
        if (response == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "挑战令牌不存在");
        return response;
    }

    /** 域名规范化并拦截空值、通配符和非法 DNS 标签。 */
    private List<String> normalizeAndValidate(List<String> requestedDomains) {
        if (requestedDomains == null || requestedDomains.isEmpty() || requestedDomains.size() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请提供 1 至 100 个域名");
        }
        List<String> names = requestedDomains.stream().map(value -> value == null ? "" : value.trim().toLowerCase())
                .distinct().toList();
        if (names.stream().anyMatch(name -> name.isBlank() || name.startsWith("*.")
                || !name.matches("(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "域名格式无效；HTTP-01 不支持通配符");
        }
        return names;
    }

    /** 第一次啟动时生成 ACME 账户密钥，后续复用以保持同一 CA 账户身份。 */
    private KeyPair loadOrCreateAccountKey() throws Exception {
        Path path = Path.of(accountKeyPath);
        if (Files.exists(path)) {
            try (var reader = Files.newBufferedReader(path)) {
                return KeyPairUtils.readKeyPair(reader);
            }
        }
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        KeyPair keyPair = KeyPairUtils.createKeyPair(2048);
        try (var writer = Files.newBufferedWriter(path)) {
            KeyPairUtils.writeKeyPair(keyPair, writer);
        }
        try {
            Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // Windows 等不支持 POSIX 权限的平台由系统文件权限保护该账户密钥。
        }
        return keyPair;
    }

    /** 为站点证书生成独立的 P-256 私钥，和 ACME 账户密钥分开保管。 */
    private KeyPair createCertificateKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    /** 轮询 HTTP-01 状态；CA 明确失败或超过配置时限时立即结束签发。 */
    private void waitForChallenge(Http01Challenge challenge) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(challengeTimeoutSeconds).toNanos();
        while (System.nanoTime() < deadline) {
            challenge.update();
            if (challenge.getStatus() == Status.VALID) return;
            if (challenge.getStatus() == Status.INVALID) {
                throw new IllegalStateException("CA 域名验证失败：" + challenge.getError());
            }
            Thread.sleep(1500);
        }
        throw new IllegalStateException("等待 CA 域名验证超时");
    }

    /** CSR 提交后等待签发完成，避免提前读取尚未生成的证书。 */
    private void waitForOrder(Order order) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(challengeTimeoutSeconds).toNanos();
        while (System.nanoTime() < deadline) {
            order.update();
            if (order.getStatus() == Status.VALID) return;
            if (order.getStatus() == Status.INVALID) throw new IllegalStateException("CA 证书订单失败：" + order.getError());
            Thread.sleep(1500);
        }
        throw new IllegalStateException("等待 CA 签发证书超时");
    }
}
