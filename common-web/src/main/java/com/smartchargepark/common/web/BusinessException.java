package com.smartchargepark.common.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Only use a deliberate, public-safe message; never pass a provider exception or request value. */
public class BusinessException extends ResponseStatusException {
    private final String code;
    private final String safeMessage;

    public BusinessException(HttpStatus status, String code, String safeMessage) {
        super(status, safeMessage);
        if (!status.isError() || code == null || !code.matches("[A-Z][A-Z0-9_]{0,63}")
                || safeMessage == null || safeMessage.isBlank()) {
            throw new IllegalArgumentException("BusinessException requires an error status, code and public-safe message");
        }
        this.code = code;
        this.safeMessage = safeMessage;
    }

    public String code() { return code; }
    public String safeMessage() { return safeMessage; }
}
