package com.cardplatform.core.shared.outbox.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// Moves committed events from the outbox table to Kafka. If Kafka is down, events simply wait here.
@Component
class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, byte[]> kafka;

    OutboxRelay(OutboxRepository outbox, KafkaTemplate<String, byte[]> kafka) {
        this.outbox = outbox;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${cardplatform.outbox.poll-interval}")
    void publishPending() {
        for (OutboxMessage message : outbox.findTop100ByPublishedAtIsNullOrderBySeqAsc()) {
            try {
                // Wait for Kafka's acknowledgement before marking the row, so nothing is marked that wasn't stored.
                // A crash between the two re-sends the event later: at-least-once, never lost.
                kafka.send(message.getTopic(), message.getMessageKey(), message.getPayload()).get(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException | TimeoutException e) {
                // Stop the batch so a later event for the same account is never published before this one
                log.warn("Outbox publish failed, will retry: id={}, topic={}, error={}",
                        message.getId(), message.getTopic(), e.getMessage());
                return;
            }
            outbox.markPublished(message.getId(), Instant.now());
            log.debug("Outbox event published: id={}, type={}", message.getId(), message.getEventType());
        }
    }
}