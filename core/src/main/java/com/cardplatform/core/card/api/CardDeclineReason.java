package com.cardplatform.core.card.api;

public enum CardDeclineReason {
    CARD_NOT_FOUND,
    CARD_NOT_ACTIVE,
    CARD_EXPIRED,
    TRANSACTION_LIMIT_EXCEEDED,
    MERCHANT_CATEGORY_BLOCKED,
    CURRENCY_NOT_SUPPORTED
}