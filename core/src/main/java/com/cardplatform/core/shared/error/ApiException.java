package com.cardplatform.core.shared.error;

import org.springframework.http.HttpStatus;

// Base for business errors; each module extends it and the global handler turns it into a response.
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}