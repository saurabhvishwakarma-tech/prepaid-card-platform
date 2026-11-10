package com.cardplatform.core.funding.dto;

import java.time.Instant;
import java.util.UUID;

public record TopUpResponse(UUID id, long amountMinor, String currency, String status, String failureCode,
                            Instant createdAt) {
}