package com.acme.domaincert.web;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** 将业务校验异常的中文原因返回给前端，避免只显示无法排查的通用错误码。 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** 保留服务指定的 HTTP 状态码，并将安全可展示的业务原因放入 message 字段。 */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException exception) {
        HttpStatusCode status = exception.getStatusCode();
        String message = exception.getReason() == null ? "请求失败" : exception.getReason();
        return ResponseEntity.status(status).body(new ApiError(status.value(), message));
    }

    /** 前端错误提示使用的精简错误体。 */
    public record ApiError(int status, String message) { }
}
