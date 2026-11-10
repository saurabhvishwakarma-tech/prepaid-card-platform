package com.cardplatform.core.card.api;

import com.cardplatform.core.shared.money.Money;

public interface CardApi {

    // The card's own checks for a payment. Whether there is enough money is the ledger's job.
    CardCheck checkForPayment(String pan, int expiryMonth, int expiryYear, Money amount, String mcc);
}