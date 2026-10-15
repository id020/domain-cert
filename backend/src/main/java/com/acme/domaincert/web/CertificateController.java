package com.acme.domaincert.web;

import com.acme.domaincert.domain.CertificateAsset;
import com.acme.domaincert.repository.CertificateRepository;
import com.acme.domaincert.service.CertificateService;
import com.acme.domaincert.service.CertificateInspectionService;
import com.acme.domaincert.service.AcmeIssuanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** 证书资产的只读列表、PEM 上传、续期偏好及受控下载接口。 */
@RestController
@RequestMapping("/api/certificates")
public class CertificateController {

    private final CertificateRepository certificates;
    private final CertificateService certificateService;
    private final CertificateInspectionService inspectionService;
    private final AcmeIssuanceService acmeIssuanceService;

    public CertificateController(CertificateRepository certificates, CertificateService certificateService,
                                 CertificateInspectionService inspectionService, AcmeIssuanceService acmeIssuanceService) {
        this.certificates = certificates;
        this.certificateService = certificateService;
        this.inspectionService = inspectionService;
        this.acmeIssuanceService = acmeIssuanceService;
    }

    /** 三种角色均可查看证书元数据，但只允许运维和管理员下载私钥。 */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public List<CertificateAsset> list() {
        return certificates.findAll();
    }

    /** 按域名读取线上 HTTPS 证书公有信息，不会下载或保存服务器私钥。 */
    @GetMapping("/inspect")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public CertificateInspectionService.Inspection inspect(@RequestParam String domain) {
        return inspectionService.inspect(domain);
    }

    /** 接收 PEM 文件，校验证书有效期和密钥匹配后加密保存私钥。 */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public CertificateAsset upload(@RequestParam("file") MultipartFile file,
                                   @RequestParam(value = "key", required = false) MultipartFile privateKey,
                                   Authentication authentication) {
        return certificateService.upload(file, privateKey, authentication);
    }

    /** 通过 ACME HTTP-01 申请证书；签发成功后系统会加密保存私钥并创建证书资产。 */
    @PostMapping("/issue")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public CertificateAsset issue(@Valid @RequestBody IssueRequest request, Authentication authentication) {
        return acmeIssuanceService.issue(request.domains(), request.email(), authentication);
    }

    /** 更新续期偏好；此标记不会绕过尚未配置的 ACME CA 执行实际续期。 */
    @PutMapping("/{id}/auto-renew")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public CertificateAsset autoRenew(@PathVariable Long id,
                                      @Valid @RequestBody AutoRenewRequest request,
                                      Authentication authentication) {
        return certificateService.setAutoRenew(id, request.enabled(), authentication);
    }

    /** 删除证书及其历史密钥版本；仅管理员和运维可执行。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public void delete(@PathVariable Long id, Authentication authentication) {
        certificateService.delete(id, authentication);
    }

    /** 下载当前版本的证书链与私钥 PEM；密钥正文只在该授权接口中解密。 */
    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        byte[] content = certificateService.downloadPem(id).getBytes(StandardCharsets.US_ASCII);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/x-pem-file"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("certificate-" + id + ".pem")
                .build());
        headers.setContentLength(content.length);
        return ResponseEntity.ok().headers(headers).body(content);
    }

    /** 续期偏好请求体。 */
    public record AutoRenewRequest(@NotNull Boolean enabled) { }

    /** ACME 签发请求：域名必须已登记，联系邮箱用于 CA 账户通知。 */
    public record IssueRequest(@NotEmpty List<String> domains, @Email @NotBlank String email) { }
}
