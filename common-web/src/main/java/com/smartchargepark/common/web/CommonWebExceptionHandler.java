package com.smartchargepark.common.web;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
@Order(-10) // Before Boot's optional ProblemDetail advice; no broader application component scan.
public class CommonWebExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(CommonWebExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> handleBusiness(BusinessException exception, WebRequest request) {
        ErrorResponse body = exception.getStatusCode().is5xxServerError()
                ? defaultError(exception.getStatusCode(), request)
                : new ErrorResponse(exception.getStatusCode().value(), exception.code(),
                        exception.safeMessage(), path(request));
        return respond(exception, body, new HttpHeaders(), exception.getStatusCode(), request);
    }

    @ExceptionHandler({ConstraintViolationException.class, BindException.class})
    public ResponseEntity<Object> handleValidation(Exception exception, WebRequest request) {
        return handleExceptionInternal(exception, null, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnknown(Exception exception, WebRequest request) {
        logUnknown(exception, request);
        return respond(exception, defaultError(HttpStatus.INTERNAL_SERVER_ERROR, request),
                new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (status.value() == 500) {
            logUnknown(exception, request);
        }
        // Never expose framework messages, rejected values or arbitrary exception reasons.
        return respond(exception, defaultError(status, request), headers, status, request);
    }

    private ResponseEntity<Object> respond(Exception exception, ErrorResponse body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        HttpHeaders safeHeaders = new HttpHeaders();
        safeHeaders.putAll(headers);
        safeHeaders.setContentType(MediaType.APPLICATION_JSON);
        safeHeaders.setCacheControl("no-store");
        return super.handleExceptionInternal(exception, body, safeHeaders, status, request);
    }

    private ErrorResponse defaultError(HttpStatusCode status, WebRequest request) {
        String code = switch (status.value()) {
            case 400 -> "INVALID_REQUEST";
            case 401 -> "AUTH_REQUIRED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 409 -> "CONFLICT";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            case 429 -> "TOO_MANY_REQUESTS";
            case 503 -> "SERVICE_UNAVAILABLE";
            default -> status.is4xxClientError() ? "REQUEST_REJECTED" : "INTERNAL_ERROR";
        };
        String message = switch (status.value()) {
            case 400 -> "请求参数或格式错误";
            case 401 -> "请先登录或重新登录";
            case 403 -> "无权执行此操作";
            case 404 -> "请求的资源不存在";
            case 405 -> "请求方法不支持";
            case 409 -> "请求与当前业务状态冲突";
            case 415 -> "请求内容类型不支持";
            case 429 -> "请求过于频繁，请稍后重试";
            case 503 -> "服务暂不可用，请稍后重试";
            default -> status.is4xxClientError() ? "请求未被接受" : "服务器内部错误";
        };
        return new ErrorResponse(status.value(), code, message, path(request));
    }

    private String path(WebRequest request) {
        return request instanceof ServletWebRequest servlet ? servlet.getRequest().getRequestURI() : "";
    }

    private void logUnknown(Exception exception, WebRequest request) {
        // Retain the original location, but not raw messages/causes that could contain SQL or credentials.
        RuntimeException safeStack = new RuntimeException("Exception details omitted: " + exception.getClass().getName());
        safeStack.setStackTrace(exception.getStackTrace());
        log.error("[UnhandledWebError] type={} path={}", exception.getClass().getName(), path(request), safeStack);
    }
}
