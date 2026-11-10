package com.cardplatform.core.funding.model;

public enum TopUpStatus {
    // Saved, not yet accepted by Stripe
    CREATED,
    // Stripe has the payment; waiting for its webhook
    PENDING,
    COMPLETED,
    FAILED
}