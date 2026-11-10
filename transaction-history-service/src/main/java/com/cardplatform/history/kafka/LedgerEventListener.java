package com.cardplatform.history.kafka;

import com.cardplatform.contracts.events.v1.LedgerPostingEvent;
import com.cardplatform.history.service.TransactionProjection;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class LedgerEventListener {

    private final TransactionProjection projection;

    LedgerEventListener(TransactionProjection projection) {
        this.projection = projection;
    }

    @KafkaListener(topics = "ledger.postings")
    void onLedgerPosting(ConsumerRecord<String, byte[]> record) throws InvalidProtocolBufferException {
        projection.apply(LedgerPostingEvent.parseFrom(record.value()));
    }
}