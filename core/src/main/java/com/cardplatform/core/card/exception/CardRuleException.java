package com.cardplatform.core.card.exception;

import com.cardplatform.core.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class CardRuleException extends ApiException {

    public CardRuleException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}