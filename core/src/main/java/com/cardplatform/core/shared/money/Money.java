package com.cardplatform.core.shared.money;

import java.util.Objects;

// Minor units (cents) and an ISO 4217 code: floating point never touches money.
public record Money(long amountMinor, String currency) {

    public static final String EUR = "EUR";

    public Money {
        Objects.requireNonNull(currency, "currency");
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency must be a three-letter ISO code: " + currency);
        }
    }

    public static Money eur(long amountMinor) {
        return new Money(amountMinor, EUR);
    }

    public boolean isPositive() {
        return amountMinor > 0;
    }
}