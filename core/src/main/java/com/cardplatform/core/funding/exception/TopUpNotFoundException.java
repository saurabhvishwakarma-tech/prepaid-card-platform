package com.cardplatform.core.funding.exception;

import com.cardplatform.core.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class TopUpNotFoundException extends ApiException {

    public TopUpNotFoundException() {
        super(HttpStatus.NOT_FOUND, "TOP_UP_NOT_FOUND", "Top-up not found");
    }
}