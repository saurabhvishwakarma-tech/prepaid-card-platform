package com.cardplatform.core.card.exception;

import com.cardplatform.core.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class CardNotFoundException extends ApiException {

    public CardNotFoundException() {
        super(HttpStatus.NOT_FOUND, "CARD_NOT_FOUND", "No active card");
    }
}