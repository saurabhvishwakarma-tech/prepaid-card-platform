package com.cardplatform.core.funding.exception;

import com.cardplatform.core.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidWebhookException extends ApiException {

    public InvalidWebhookException() {
        super(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK_SIGNATURE", "Webhook signature could not be verified");
    }
}