package com.cardplatform.history.dto;

import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(UUID id, String type, String direction, String status, long amountMinor,
                                  String currency, String merchantName, String category, String declineReason,
                                  Instant occurredAt) {
}