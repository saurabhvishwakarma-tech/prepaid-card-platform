package com.cardplatform.core.ledger.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// Up to €1,000 per call
public record DevTopUpRequest(@NotNull @Positive @Max(100_000) Long amountMinor) {
}