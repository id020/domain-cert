package com.acme.domaincert.web;

import com.acme.domaincert.service.SystemSettingsService;
import com.acme.domaincert.service.SystemSettingsService.SettingsUpdate;
import com.acme.domaincert.service.SystemSettingsService.SettingsView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 系统动态配置接口，仅管理员可查看或修改敏感设置。 */
@RestController
@RequestMapping("/api/settings")
@PreAuthorize("hasRole('ADMIN')")
public class SystemSettingsController {

    private final SystemSettingsService settings;

    public SystemSettingsController(SystemSettingsService settings) { this.settings = settings; }

    /** 返回邮件配置和密钥设置状态；响应不包含密码或任何密钥明文。 */
    @GetMapping
    public SettingsView get() { return settings.view(); }

    /** 更新配置；空白的 SMTP 密码或证书密钥表示保留现有值。 */
    @PutMapping
    public SettingsView update(@Valid @RequestBody SettingsUpdateRequest request, Authentication actor) {
        return settings.update(new SettingsUpdate(request.smtpHost(), request.smtpPort(), request.smtpUsername(),
                request.smtpPassword(), request.smtpAuth(), request.startTls(), request.fromAddress(),
                request.globalRecipients(), request.certificateEncryptionKey()), actor);
    }

    /** SMTP 配置输入；证书密钥必须是 Base64 AES-256，具体长度在服务层二次校验。 */
    public record SettingsUpdateRequest(
            @NotBlank @Size(max = 255) String smtpHost,
            @Min(1) @Max(65535) int smtpPort,
            @Size(max = 254) String smtpUsername,
            @Size(max = 256) String smtpPassword,
            boolean smtpAuth,
            boolean startTls,
            @NotBlank @Email @Size(max = 254) String fromAddress,
            @Size(max = 1200) String globalRecipients,
            @Size(max = 64) String certificateEncryptionKey) { }
}
