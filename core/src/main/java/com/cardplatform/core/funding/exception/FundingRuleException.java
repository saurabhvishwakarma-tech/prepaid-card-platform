package com.cardplatform.core.funding.exception;

import com.cardplatform.core.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class FundingRuleException extends ApiException {

    public FundingRuleException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
