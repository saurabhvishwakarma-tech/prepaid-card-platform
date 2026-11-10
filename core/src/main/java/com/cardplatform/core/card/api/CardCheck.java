package com.cardplatform.core.card.api;

import java.util.UUID;

public record CardCheck(boolean approved, UUID cardId, UUID accountId, UUID userId, MerchantCategory category,
                        CardDeclineReason declineReason) {

    public static CardCheck approve(UUID cardId, UUID accountId, UUID userId, MerchantCategory category) {
        return new CardCheck(true, cardId, accountId, userId, category, null);
    }

    // The IDs are null when no card matches the number
    public static CardCheck decline(UUID cardId, UUID accountId, UUID userId, MerchantCategory category,
                                    CardDeclineReason reason) {
        return new CardCheck(false, cardId, accountId, userId, category, reason);
    }
}