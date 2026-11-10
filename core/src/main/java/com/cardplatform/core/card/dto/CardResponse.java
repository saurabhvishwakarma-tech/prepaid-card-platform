package com.cardplatform.core.card.dto;

import com.cardplatform.core.card.api.MerchantCategory;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record CardResponse(UUID id, String maskedPan, int expiryMonth, int expiryYear, String status,
                           long perTransactionLimitMinor, Set<MerchantCategory> blockedCategories,
                           Instant createdAt) {
}