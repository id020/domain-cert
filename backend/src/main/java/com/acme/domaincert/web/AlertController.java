package com.acme.domaincert.web;

import com.acme.domaincert.domain.AlertDelivery;
import com.acme.domaincert.service.ExpiryAlertService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 查看邮件提醒发送记录的接口。 */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final ExpiryAlertService alertService;

    public AlertController(ExpiryAlertService alertService) {
        this.alertService = alertService;
    }

    /** 管理员、运维和只读用户均可查看提醒发送状态。 */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public List<AlertDelivery> recent() {
        return alertService.recentDeliveries();
    }

    /** 预览与自动到期提醒相同的邮件主题和正文，不发送邮件。 */
    @GetMapping("/test-preview")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public ExpiryAlertService.MailContent previewTestMail() {
        return alertService.previewTestMail();
    }

    /** 向指定收件人发送一次真实测试邮件，仅管理员和运维可触发外部邮件投递。 */
    @PostMapping("/test-email")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendTestMail(@Valid @RequestBody TestMailRequest request) {
        alertService.sendTestMail(request.recipient());
    }

    /** 测试邮件收件人由操作人员填写，并在发送前进行邮箱格式校验。 */
    public record TestMailRequest(@Email @NotNull String recipient) { }
}
