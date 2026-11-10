package com.cardplatform.simulator.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record PurchaseRequest(
        @NotBlank @Pattern(regexp = "\\d{12,19}") String pan,
        @Min(1) @Max(12) int expiryMonth,
        @Min(2000) @Max(2100) int expiryYear,
        @NotBlank String merchantId,
        @Positive long amountMinor) {

    // The card number must never reach a log
    @Override
    public String toString() {
        return "PurchaseRequest[merchantId=" + merchantId + ", amountMinor=" + amountMinor + "]";
    }
}