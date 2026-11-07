package com.cardplatform.core.identity.exception;

import com.cardplatform.core.shared.error.ApiException;
import org.springframework.http.HttpStatus;

// Same message for unknown email and wrong password, so a login attempt never reveals which accounts exist
public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
    }
}