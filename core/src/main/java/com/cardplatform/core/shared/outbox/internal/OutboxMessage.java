package com.cardplatform.core.shared.outbox.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(schema = "messaging", name = "outbox")
public class OutboxMessage {

    @Id
    private UUID id;

    // Assigned by the database identity column
    @Column(insertable = false, updatable = false)
    private Long seq;

    private String topic;

    private String messageKey;

    private String eventType;

    private byte[] payload;

    private Instant createdAt;

    private Instant publishedAt;

    @Version
    private Long version;

    protected OutboxMessage() {
    }

    public static OutboxMessage create(UUID id, String topic, String key, String eventType, byte[] payload) {
        OutboxMessage message = new OutboxMessage();
        message.id = id;
        message.topic = topic;
        message.messageKey = key;
        message.eventType = eventType;
        message.payload = payload;
        message.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return message;
    }

    public UUID getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getEventType() {
        return eventType;
    }

    public byte[] getPayload() {
        return payload;
    }
}