package com.acme.domaincert.service;

import com.acme.domaincert.domain.DomainAsset;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import javax.naming.Context;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * 聚合 RDAP 注册数据与 DNS 公共记录查询。
 * RDAP 是 WHOIS 的结构化替代方案；RDAP 无数据时不会臆造注册日期等字段。
 */
@Service
public class DomainLookupService {

    private static final List<String> DNS_TYPES = List.of("A", "AAAA", "CNAME", "MX", "NS", "TXT", "CAA", "SOA");
    private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(7);
    private static final int WHOIS_TIMEOUT_MS = 5000;
    private static final int WHOIS_MAX_BYTES = 65536;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DomainLookupService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(QUERY_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** 查询单个域名；某一查询源失败时尽量保留另一数据源的有效结果。 */
    public DomainAsset.LookupResult lookup(String domainName) {
        RdapResult rdap = lookupRdap(domainName);
        // .cn 注册局当前通过 CNNIC WHOIS 提供公开注册资料，RDAP 无结果时必须走该回退源。
        if ("cn".equalsIgnoreCase(topLevelDomain(domainName))
                && (rdap.registeredAt() == null || rdap.expiresAt() == null || rdap.registrar() == null)) {
            rdap = mergeWhois(rdap, lookupCnnicWhois(domainName));
        }
        List<Map<String, String>> dnsRecords = lookupDns(domainName);

        // RDAP 的 NS 和 DNS NS 记录去重合并，避免注册数据未返回 NS 时页面空白。
        Set<String> nameServers = new LinkedHashSet<>(rdap.nameServers());
        dnsRecords.stream()
                .filter(record -> "NS".equals(record.get("type")))
                .map(record -> record.get("value"))
                .filter(value -> value != null && !value.isBlank())
                // DNS 响应常带末尾根点，统一格式后再与 RDAP 名称服务器去重。
                .map(value -> value.replaceFirst("\\.$", "").toLowerCase())
                .forEach(nameServers::add);

        String lookupStatus = rdap.available() && !dnsRecords.isEmpty()
                ? "SUCCESS"
                : rdap.available() || !dnsRecords.isEmpty() ? "PARTIAL" : "FAILED";
        String message = lookupStatus.equals("SUCCESS") ? null
                : lookupStatus.equals("PARTIAL") ? "部分公开数据源暂不可用" : "RDAP 和 DNS 查询均未取得结果";

        try {
            return new DomainAsset.LookupResult(
                    rdap.registeredAt(),
                    rdap.expiresAt(),
                    rdap.registrar(),
                    String.join(",", nameServers),
                    objectMapper.writeValueAsString(dnsRecords),
                    lookupStatus,
                    message);
        } catch (Exception exception) {
            // JSON 序列化失败不应丢失域名的其他公开信息。
            return new DomainAsset.LookupResult(rdap.registeredAt(), rdap.expiresAt(), rdap.registrar(),
                    String.join(",", nameServers), "[]", "PARTIAL", "DNS 结果格式化失败");
        }
    }

    /** 通过 CNNIC 官方 WHOIS TCP 服务读取 .cn 域名的注册商、日期和注册局名称服务器。 */
    private RdapResult lookupCnnicWhois(String domainName) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("whois.cnnic.cn", 43), WHOIS_TIMEOUT_MS);
            socket.setSoTimeout(WHOIS_TIMEOUT_MS);
            socket.getOutputStream().write((domainName + "\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();

            // WHOIS 是逐行文本协议；设置大小上限，避免异常服务端返回无限响应。
            java.io.ByteArrayOutputStream response = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            int totalBytes = 0;
            int count;
            while (true) {
                try {
                    count = socket.getInputStream().read(buffer);
                } catch (java.net.SocketTimeoutException timeout) {
                    // 部分 WHOIS 服务器发送完整记录后不主动关闭连接；收到内容后即可解析。
                    if (totalBytes > 0) break;
                    throw timeout;
                }
                if (count == -1) break;
                totalBytes += count;
                if (totalBytes > WHOIS_MAX_BYTES) throw new IllegalArgumentException("WHOIS 响应超出大小限制");
                response.write(buffer, 0, count);
            }

            String text = response.toString(java.nio.charset.StandardCharsets.UTF_8);
            LocalDate registeredAt = null;
            LocalDate expiresAt = null;
            String registrar = null;
            List<String> nameServers = new ArrayList<>();
            for (String line : text.split("\\R")) {
                int separator = line.indexOf(':');
                if (separator < 0) continue;
                String key = line.substring(0, separator).trim().toLowerCase();
                String value = line.substring(separator + 1).trim();
                if (value.isBlank()) continue;
                if (key.equals("registration time")) registeredAt = parseDate(value);
                else if (key.equals("expiration time")) expiresAt = parseDate(value);
                else if (key.equals("sponsoring registrar")) registrar = value;
                else if (key.equals("name server")) nameServers.add(value.toLowerCase());
            }
            return new RdapResult(registeredAt, expiresAt, registrar, nameServers, !text.isBlank());
        } catch (Exception exception) {
            return RdapResult.unavailable();
        }
    }

    /** 合并 RDAP 与 WHOIS 字段，以 RDAP 为优先来源并用 WHOIS 补齐缺失内容。 */
    private RdapResult mergeWhois(RdapResult rdap, RdapResult whois) {
        Set<String> servers = new LinkedHashSet<>(rdap.nameServers());
        servers.addAll(whois.nameServers());
        return new RdapResult(rdap.registeredAt() != null ? rdap.registeredAt() : whois.registeredAt(),
                rdap.expiresAt() != null ? rdap.expiresAt() : whois.expiresAt(),
                rdap.registrar() != null ? rdap.registrar() : whois.registrar(),
                new ArrayList<>(servers), rdap.available() || whois.available());
    }

    /** 获取经输入校验后的域名顶级标签。 */
    private String topLevelDomain(String domainName) {
        return domainName.substring(domainName.lastIndexOf('.') + 1);
    }

    /** 使用公共 RDAP 聚合服务查询注册商、注册时间、过期时间和名称服务器。 */
    private RdapResult lookupRdap(String domainName) {
        // 先尝试注册局原生端点；以 .com/.net 的 Verisign RDAP 为例，可避免聚合服务的跳转故障。
        String tld = domainName.substring(domainName.lastIndexOf('.') + 1).toLowerCase();
        if ("com".equals(tld) || "net".equals(tld)) {
            RdapResult registryResult = requestRdap("https://rdap.verisign.com/" + tld
                    + "/v1/domain/" + domainName);
            if (registryResult.available()) return registryResult;
        }
        return requestRdap("https://rdap.org/domain/" + domainName);
    }

    /** 发起一条 RDAP 请求并解析核心注册字段；连接或响应异常交由备用端点接手。 */
    private RdapResult requestRdap(String endpoint) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(QUERY_TIMEOUT)
                    .header("Accept", "application/rdap+json, application/json")
                    .header("User-Agent", "DomainCertConsole/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return RdapResult.unavailable();
            }

            JsonNode root = objectMapper.readTree(response.body());
            LocalDate registeredAt = null;
            LocalDate expiresAt = null;
            LocalDate registrarExpiresAt = null;
            JsonNode events = root.path("events");
            for (JsonNode event : events) {
                String action = event.path("eventAction").asText("").toLowerCase();
                LocalDate eventDate = parseDate(event.path("eventDate").asText(null));
                if (eventDate == null) continue;
                // 不同注册局使用的动作名称并不完全一致，兼容常见的注册、到期表达。
                if (action.contains("registration") || action.contains("registered")) registeredAt = eventDate;
                if (action.contains("registrar expiration")) {
                    registrarExpiresAt = eventDate;
                } else if (action.contains("expiration") || action.contains("expiry") || action.contains("expire")) {
                    expiresAt = eventDate;
                }
            }
            if (expiresAt == null) expiresAt = registrarExpiresAt;

            String registrar = findRegistrar(root.path("entities"));
            if (registrar == null) {
                // 少数 RDAP 服务会将注册商名称直接放在域名对象上。
                registrar = firstText(root, "registrar", "registrarName", "registrar_name");
            }
            List<String> nameServers = new ArrayList<>();
            for (JsonNode server : root.path("nameservers")) {
                String name = server.path("ldhName").asText("");
                if (!name.isBlank()) nameServers.add(name.toLowerCase());
            }
            return new RdapResult(registeredAt, expiresAt, registrar, nameServers, true);
        } catch (Exception exception) {
            return RdapResult.unavailable();
        }
    }

    /** 遍历 RDAP 组织实体，优先使用角色为 registrar 的名称。 */
    private String findRegistrar(JsonNode entities) {
        for (JsonNode entity : entities) {
            boolean registrar = false;
            for (JsonNode role : entity.path("roles")) {
                if ("registrar".equalsIgnoreCase(role.asText())) registrar = true;
            }
            if (!registrar) continue;
            String name = vcardName(entity.path("vcardArray"));
            if (name != null) return name;
            // RDAP 允许实体中继续嵌套实体，注册商信息可能位于其子实体。
            String nested = findRegistrar(entity.path("entities"));
            if (nested != null) return nested;
        }
        return null;
    }

    /** 兼容 vCard 中 fn 与 org 两种组织名称字段。 */
    private String vcardName(JsonNode vcard) {
        JsonNode properties = vcard.path(1);
        String organization = null;
        for (JsonNode item : properties) {
            String key = item.path(0).asText("");
            String value = item.path(3).isArray()
                    ? item.path(3).path(0).asText(null)
                    : item.path(3).asText(null);
            if (value == null || value.isBlank()) continue;
            if ("fn".equalsIgnoreCase(key)) return value;
            if ("org".equalsIgnoreCase(key)) organization = value;
        }
        return organization;
    }

    /** 从若干注册局常见字段名中取第一个非空文本值。 */
    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText(null);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    /** 用 JNDI DNS provider 查询常用记录；每种类型失败时单独跳过。 */
    private List<Map<String, String>> lookupDns(String domainName) {
        List<Map<String, String>> records = new ArrayList<>();
        Properties environment = new Properties();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        environment.put(Context.PROVIDER_URL, "dns:");
        environment.put("com.sun.jndi.dns.timeout.initial", "2500");
        environment.put("com.sun.jndi.dns.timeout.retries", "1");

        DirContext context = null;
        try {
            context = new InitialDirContext(environment);
            for (String type : DNS_TYPES) {
                try {
                    Attributes attributes = context.getAttributes(domainName, new String[]{type});
                    Attribute attribute = attributes.get(type);
                    if (attribute == null) continue;
                    for (int index = 0; index < attribute.size(); index++) {
                        records.add(Map.of("type", type, "value", String.valueOf(attribute.get(index))));
                    }
                } catch (Exception ignored) {
                    // 单条记录不存在属于正常情况，不影响其他 DNS 类型查询。
                }
            }
        } catch (Exception ignored) {
            // DNS 服务不可达时返回空列表，由调用方写入 PARTIAL/FAILED 状态。
        } finally {
            if (context != null) {
                try {
                    context.close();
                } catch (Exception ignored) {
                    // 关闭查询上下文失败不影响已获得的数据。
                }
            }
        }
        return records;
    }

    /** 将 RDAP 时间解析为日期；兼容带时区和纯日期两种常见格式。 */
    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return OffsetDateTime.parse(value).toLocalDate();
        } catch (Exception ignored) {
            try {
                return Instant.parse(value).atZone(java.time.ZoneOffset.UTC).toLocalDate();
            } catch (Exception ignoredAgain) {
                try {
                    return LocalDate.parse(value.substring(0, 10));
                } catch (Exception invalidDate) {
                    return null;
                }
            }
        }
    }

    private record RdapResult(LocalDate registeredAt, LocalDate expiresAt, String registrar,
                              List<String> nameServers, boolean available) {
        private static RdapResult unavailable() {
            return new RdapResult(null, null, null, List.of(), false);
        }
    }
}
