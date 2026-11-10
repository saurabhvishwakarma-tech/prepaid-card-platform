package com.cardplatform.history.config;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
class KafkaConfig {

    // Named explicitly rather than relying on the library's default suffix
    private static final String DLT_SUFFIX = ".DLT";

    // The dead-letter topics belong to this consumer, with the same partition count as their source topics
    @Bean
    NewTopic paymentEventsDeadLetterTopic() {
        return TopicBuilder.name("payments.events" + DLT_SUFFIX).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic ledgerPostingsDeadLetterTopic() {
        return TopicBuilder.name("ledger.postings" + DLT_SUFFIX).partitions(3).replicas(1).build();
    }

    // Retry a failing message 3 times, 1 second apart, then park it on <topic>.DLT so the rest keep flowing.
    // A message that isn't valid Protobuf will never parse, so it goes straight to the DLT.
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, byte[]> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, exception) -> new TopicPartition(record.topic() + DLT_SUFFIX, record.partition()));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 3));
        handler.addNotRetryableExceptions(InvalidProtocolBufferException.class);
        return handler;
    }
}