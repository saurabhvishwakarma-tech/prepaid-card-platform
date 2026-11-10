package com.cardplatform.simulator.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AmountRequest(@NotNull @Positive Long amountMinor) {
}