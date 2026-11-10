package com.cardplatform.core.funding.provider;

// A verified webhook event; payment is null for event types that aren't about a payment
public record ProviderEvent(String id, String type, ProviderPayment payment) {

    public static final String PAYMENT_SUCCEEDED = "payment_intent.succeeded";
    public static final String PAYMENT_FAILED = "payment_intent.payment_failed";
}