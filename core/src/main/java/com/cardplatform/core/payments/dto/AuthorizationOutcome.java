package com.cardplatform.core.payments.dto;

import com.cardplatform.core.payments.model.PaymentDeclineReason;

public record AuthorizationOutcome(boolean approved, String authCode, PaymentDeclineReason declineReason) {

    public static AuthorizationOutcome approved(String authCode) {
        return new AuthorizationOutcome(true, authCode, null);
    }

    public static AuthorizationOutcome declined(PaymentDeclineReason reason) {
        return new AuthorizationOutcome(false, null, reason);
    }
}