package com.cardplatform.core.payments.model;

public enum OperationRejectReason {
    AUTHORIZATION_NOT_FOUND,
    INVALID_STATE,
    AMOUNT_EXCEEDS_ALLOWED,
    CURRENCY_MISMATCH
}