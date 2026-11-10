package com.cardplatform.core.card.dto;

import com.cardplatform.core.card.api.MerchantCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Set;

public record UpdateControlsRequest(
        // Up to €5,000 per transaction
        @NotNull @Positive @Max(500_000) Long perTransactionLimitMinor,
        @NotNull Set<MerchantCategory> blockedCategories) {
}