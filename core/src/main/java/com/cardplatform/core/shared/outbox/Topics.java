package com.cardplatform.core.shared.outbox;

// Kafka topics owned by core
public final class Topics {

    public static final String PAYMENT_EVENTS = "payments.events";
    public static final String LEDGER_POSTINGS = "ledger.postings";
    public static final String CARD_LIFECYCLE = "card.lifecycle";
    public static final String IDENTITY_EVENTS = "identity.events";

    private Topics() {
    }
}