package com.cardplatform.core.ledger.exception;

import com.cardplatform.core.shared.error.ApiException;
import org.springframework.http.HttpStatus;

// A request that would break a ledger rule; callers are expected to check before asking.
public class LedgerRuleException extends ApiException {

    public LedgerRuleException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}