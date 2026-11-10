package com.cardplatform.core.funding.exception;

import com.cardplatform.core.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidIdempotencyKeyException extends ApiException {

    public InvalidIdempotencyKeyException() {
        super(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                "Idempotency-Key must be 8-64 letters, digits, '-' or '_'");
    }
}