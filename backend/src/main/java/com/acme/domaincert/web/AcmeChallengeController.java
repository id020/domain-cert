package com.acme.domaincert.web;

import com.acme.domaincert.service.AcmeIssuanceService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Let's Encrypt HTTP-01 验证入口，由 Nginx 从公网 80 端口转发。 */
@RestController
public class AcmeChallengeController {

    private final AcmeIssuanceService acmeIssuanceService;

    public AcmeChallengeController(AcmeIssuanceService acmeIssuanceService) {
        this.acmeIssuanceService = acmeIssuanceService;
    }

    /** 只响应当前签发事务发布的验证令牌，其他请求均返回 404。 */
    @GetMapping(value = "/.well-known/acme-challenge/{token}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String challenge(@PathVariable String token) {
        return acmeIssuanceService.challengeResponse(token);
    }
}
