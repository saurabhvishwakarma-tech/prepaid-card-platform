package com.cardplatform.simulator.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record LoadRequest(
        @NotBlank @Pattern(regexp = "\\d{12,19}") String pan,
        @Min(1) @Max(12) int expiryMonth,
        @Min(2000) @Max(2100) int expiryYear,
        @NotBlank String merchantId,
        @Positive long amountMinor,
        @Min(1) @Max(1000) int count,
        @Min(1) @Max(50) int concurrency) {

    public PurchaseRequest toPurchase() {
        return new PurchaseRequest(pan, expiryMonth, expiryYear, merchantId, amountMinor);
    }

    @Override
    public String toString() {
        return "LoadRequest[merchantId=" + merchantId + ", amountMinor=" + amountMinor
                + ", count=" + count + ", concurrency=" + concurrency + "]";
    }
}