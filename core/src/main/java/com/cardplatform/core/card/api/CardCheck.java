package com.cardplatform.core.card.api;

import java.util.UUID;

public record CardCheck(boolean approved, UUID cardId, UUID accountId, MerchantCategory category,
                        CardDeclineReason declineReason) {

    public static CardCheck approve(UUID cardId, UUID accountId, MerchantCategory category) {
        return new CardCheck(true, cardId, accountId, category, null);
    }

    // cardId and accountId are null when no card matches the number
    public static CardCheck decline(UUID cardId, UUID accountId, MerchantCategory category,
                                    CardDeclineReason reason) {
        return new CardCheck(false, cardId, accountId, category, reason);
    }
}