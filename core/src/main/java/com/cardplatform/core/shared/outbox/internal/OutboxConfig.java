package com.cardplatform.core.shared.outbox.internal;

import com.cardplatform.core.shared.outbox.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

// Core creates the topics it owns at startup; the broker refuses to auto-create them (Day 1)
@Configuration
@EnableScheduling
class OutboxConfig {

    @Bean
    NewTopic paymentEventsTopic() {
        return topic(Topics.PAYMENT_EVENTS);
    }

    @Bean
    NewTopic ledgerPostingsTopic() {
        return topic(Topics.LEDGER_POSTINGS);
    }

    @Bean
    NewTopic cardLifecycleTopic() {
        return topic(Topics.CARD_LIFECYCLE);
    }

    @Bean
    NewTopic identityEventsTopic() {
        return topic(Topics.IDENTITY_EVENTS);
    }

    // Three partitions so a consumer can later run as up to three instances; one replica on a single broker
    private static NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }
}