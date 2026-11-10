package com.cardplatform.core.funding.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(schema = "funding", name = "stripe_events")
public class StripeEvent {

    @Id
    private String eventId;

    private String eventType;

    private Instant receivedAt;

    protected StripeEvent() {
    }

    public static StripeEvent received(String eventId, String eventType) {
        StripeEvent event = new StripeEvent();
        event.eventId = eventId;
        event.eventType = eventType;
        event.receivedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return event;
    }
}