package com.cardplatform.core.funding.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// paymentMethodId is a Stripe token (pm_...), never a card number
public record TopUpRequest(
        @NotNull @Min(100) @Max(100_000) Long amountMinor,
        @NotNull @Pattern(regexp = "pm_[A-Za-z0-9_]{1,96}") String paymentMethodId) {
}