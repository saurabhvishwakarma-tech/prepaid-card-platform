package com.cardplatform.core.shared.outbox;

import com.cardplatform.contracts.common.v1.EventMetadata;
import com.cardplatform.core.shared.outbox.internal.OutboxMessage;
import com.cardplatform.core.shared.outbox.internal.OutboxRepository;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Timestamp;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

// The only way a module publishes an event: by saving it next to the change it describes
@Component
public class OutboxWriter {

    private final OutboxRepository outbox;

    public OutboxWriter(OutboxRepository outbox) {
        this.outbox = outbox;
    }

    // MANDATORY: refuses to run outside a transaction, so an event can never be written on its own
    @Transactional(propagation = Propagation.MANDATORY)
    public void write(String topic, String key, String eventType, EventMetadata metadata, MessageLite event) {
        outbox.save(OutboxMessage.create(UUID.fromString(metadata.getEventId()), topic, key, eventType,
                event.toByteArray()));
    }

    // The event ID doubles as the outbox row ID; consumers use it to skip events they have already applied
    public static EventMetadata newMetadata() {
        Instant now = Instant.now();
        return EventMetadata.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Timestamp.newBuilder().setSeconds(now.getEpochSecond()).setNanos(now.getNano()))
                .build();
    }
}