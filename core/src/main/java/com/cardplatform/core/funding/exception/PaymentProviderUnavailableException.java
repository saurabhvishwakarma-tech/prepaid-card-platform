package com.cardplatform.core.funding.exception;

import com.cardplatform.core.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class PaymentProviderUnavailableException extends ApiException {

    public PaymentProviderUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_PROVIDER_UNAVAILABLE",
                "Card payments are unavailable right now; retry with the same Idempotency-Key");
    }
}