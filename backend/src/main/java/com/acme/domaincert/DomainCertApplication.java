package com.acme.domaincert;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;

/** 域名与证书管理服务的 Spring Boot 启动入口。 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class DomainCertApplication {

    public static void main(String[] args) {
        SpringApplication.run(DomainCertApplication.class, args);
    }
}
