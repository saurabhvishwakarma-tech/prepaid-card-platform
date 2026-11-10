package com.cardplatform.history.kafka;

import com.cardplatform.contracts.events.v1.PaymentEvent;
import com.cardplatform.history.service.TransactionProjection;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class PaymentEventListener {

    private final TransactionProjection projection;

    PaymentEventListener(TransactionProjection projection) {
        this.projection = projection;
    }

    @KafkaListener(topics = "payments.events")
    void onPaymentEvent(ConsumerRecord<String, byte[]> record) throws InvalidProtocolBufferException {
        projection.apply(PaymentEvent.parseFrom(record.value()));
    }
}