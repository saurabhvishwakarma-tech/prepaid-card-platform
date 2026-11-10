package com.cardplatform.history.service;

import com.cardplatform.contracts.common.v1.EventMetadata;
import com.cardplatform.contracts.common.v1.Money;
import com.cardplatform.contracts.events.v1.EntryType;
import com.cardplatform.contracts.events.v1.LedgerPostingEvent;
import com.cardplatform.contracts.events.v1.PaymentEvent;
import com.cardplatform.history.model.ProcessedEvent;
import com.cardplatform.history.model.Transaction;
import com.cardplatform.history.model.TransactionStatus;
import com.cardplatform.history.model.TransactionType;
import com.cardplatform.history.repository.ProcessedEventRepository;
import com.cardplatform.history.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

// Turns core's events into the customer's transaction list. Each event is applied in one transaction
// together with its event ID, so a redelivered event is recognised and skipped.
@Service
public class TransactionProjection {

    private static final Logger log = LoggerFactory.getLogger(TransactionProjection.class);

    private final TransactionRepository transactions;
    private final ProcessedEventRepository processedEvents;

    public TransactionProjection(TransactionRepository transactions, ProcessedEventRepository processedEvents) {
        this.transactions = transactions;
        this.processedEvents = processedEvents;
    }

    @Transactional
    public void apply(PaymentEvent event) {
        if (!markProcessed(event.getMetadata())) {
            return;
        }
        Instant at = occurredAt(event.getMetadata());
        switch (event.getEventCase()) {
            case AUTHORIZED -> recordPayment(event, TransactionStatus.PENDING, event.getAuthorized().getAmount(), null);
            case DECLINED -> recordPayment(event, TransactionStatus.DECLINED, event.getDeclined().getAmount(),
                    withoutPrefix(event.getDeclined().getReason().name(), "DECLINE_REASON_"));
            case CLEARED -> paymentFor(event, event.getCleared().getClearedAmount())
                    .complete(event.getCleared().getClearedAmount().getAmountMinor(), at);
            case REVERSED -> paymentFor(event, event.getReversed().getReleasedAmount()).reverse(at);
            case REFUNDED -> transactions.save(Transaction.create(TransactionType.REFUND,
                    event.getRefunded().getRefundId(), UUID.fromString(event.getUserId()),
                    UUID.fromString(event.getAccountId()), TransactionStatus.COMPLETED,
                    event.getRefunded().getAmount().getAmountMinor(), event.getRefunded().getAmount().getCurrency(),
                    event.getMerchant().getName(), category(event), null, at));
            case EVENT_NOT_SET -> log.warn("Payment event without a type: eventId={}", event.getMetadata().getEventId());
        }
    }

    @Transactional
    public void apply(LedgerPostingEvent event) {
        if (!markProcessed(event.getMetadata())) {
            return;
        }
        // Card captures and refunds already arrive as payment events; only top-ups are new information here
        if (event.getEntryType() != EntryType.ENTRY_TYPE_TOP_UP
                || transactions.existsByTypeAndReference(TransactionType.TOP_UP, event.getReference())) {
            return;
        }
        transactions.save(Transaction.create(TransactionType.TOP_UP, event.getReference(),
                UUID.fromString(event.getUserId()), UUID.fromString(event.getAccountId()),
                TransactionStatus.COMPLETED, event.getAmount().getAmountMinor(), event.getAmount().getCurrency(),
                null, null, null, occurredAt(event.getMetadata())));
    }

    // A later event may already have created the row, in which case the earlier one adds nothing
    private void recordPayment(PaymentEvent event, TransactionStatus status, Money amount, String declineReason) {
        if (transactions.existsByTypeAndReference(TransactionType.CARD_PAYMENT, event.getNetworkTxnId())) {
            return;
        }
        transactions.save(newPayment(event, status, amount, declineReason));
    }

    // Every payment event carries the full context, so even one arriving before the authorisation can build the row
    private Transaction paymentFor(PaymentEvent event, Money amount) {
        return transactions.findByTypeAndReference(TransactionType.CARD_PAYMENT, event.getNetworkTxnId())
                .orElseGet(() -> transactions.save(newPayment(event, TransactionStatus.PENDING, amount, null)));
    }

    private static Transaction newPayment(PaymentEvent event, TransactionStatus status, Money amount,
                                          String declineReason) {
        return Transaction.create(TransactionType.CARD_PAYMENT, event.getNetworkTxnId(),
                UUID.fromString(event.getUserId()), UUID.fromString(event.getAccountId()), status,
                amount.getAmountMinor(), amount.getCurrency(), event.getMerchant().getName(), category(event),
                declineReason, occurredAt(event.getMetadata()));
    }

    private boolean markProcessed(EventMetadata metadata) {
        UUID eventId = UUID.fromString(metadata.getEventId());
        if (processedEvents.existsById(eventId)) {
            log.debug("Duplicate event skipped: eventId={}", eventId);
            return false;
        }
        processedEvents.save(new ProcessedEvent(eventId));
        return true;
    }

    private static String category(PaymentEvent event) {
        return withoutPrefix(event.getCategory().name(), "MERCHANT_CATEGORY_");
    }

    private static Instant occurredAt(EventMetadata metadata) {
        return Instant.ofEpochSecond(metadata.getOccurredAt().getSeconds(), metadata.getOccurredAt().getNanos());
    }

    private static String withoutPrefix(String name, String prefix) {
        return name.startsWith(prefix) ? name.substring(prefix.length()) : name;
    }
}