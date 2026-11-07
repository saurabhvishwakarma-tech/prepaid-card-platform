package com.cardplatform.core.identity.exception;

import com.cardplatform.core.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class UserNotFoundException extends ApiException {

    public UserNotFoundException() {
        super(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");
    }
}