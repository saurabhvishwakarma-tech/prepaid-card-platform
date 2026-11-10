package com.cardplatform.core.funding.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("cardplatform.stripe")
public record StripeProperties(@NotBlank String secretKey, @NotBlank String webhookSecret) {

    public StripeProperties {
        // A live key would move real money; this project only ever runs against Stripe's test mode
        if (secretKey != null && !secretKey.startsWith("sk_test_") && !secretKey.startsWith("rk_test_")) {
            throw new IllegalArgumentException("cardplatform.stripe.secret-key must be a test-mode key");
        }
    }

    @Override
    public String toString() {
        return "StripeProperties[secretKey=****, webhookSecret=****]";
    }
}