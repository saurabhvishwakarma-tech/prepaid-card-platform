package com.cardplatform.core.funding.provider;

import java.util.UUID;

// A payment as the provider sees it. topUpId is null when the payment wasn't made by this platform.
public record ProviderPayment(String id, String status, long amountMinor, String currency, UUID topUpId) {

    public boolean succeeded() {
        return "succeeded".equals(status);
    }

    // Stripe moves a declined payment back to requires_payment_method
    public boolean failed() {
        return "canceled".equals(status) || "requires_payment_method".equals(status);
    }

    public boolean needsCustomerAction() {
        return "requires_action".equals(status);
    }
}