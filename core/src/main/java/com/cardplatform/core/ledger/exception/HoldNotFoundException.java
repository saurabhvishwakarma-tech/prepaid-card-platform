package com.cardplatform.core.ledger.exception;

import com.cardplatform.core.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class HoldNotFoundException extends ApiException {

    public HoldNotFoundException() {
        super(HttpStatus.NOT_FOUND, "HOLD_NOT_FOUND", "Hold not found");
    }
}