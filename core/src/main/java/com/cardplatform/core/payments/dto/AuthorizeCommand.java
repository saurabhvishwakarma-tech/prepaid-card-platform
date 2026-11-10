package com.cardplatform.core.payments.dto;

import com.cardplatform.core.shared.money.Money;

public record AuthorizeCommand(String networkTxnId, String pan, int expiryMonth, int expiryYear, Money amount,
                               String merchantId, String merchantName, String mcc, String merchantCountry) {

    // Records print every field by default; the card number must never reach a log or error message
    @Override
    public String toString() {
        return "AuthorizeCommand[networkTxnId=" + networkTxnId + ", amount=" + amount + ", mcc=" + mcc + "]";
    }
}