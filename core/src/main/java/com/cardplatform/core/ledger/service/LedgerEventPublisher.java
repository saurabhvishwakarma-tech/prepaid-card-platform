package com.cardplatform.core.ledger.service;

import com.cardplatform.contracts.common.v1.EventMetadata;
import com.cardplatform.contracts.common.v1.Money;
import com.cardplatform.contracts.events.v1.LedgerPostingEvent;
import com.cardplatform.contracts.events.v1.PostingDirection;
import com.cardplatform.core.ledger.model.Account;
import com.cardplatform.core.ledger.model.EntryType;
import com.cardplatform.core.shared.outbox.OutboxWriter;
import com.cardplatform.core.shared.outbox.Topics;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class LedgerEventPublisher {

    private final OutboxWriter outbox;

    LedgerEventPublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    // The customer's side of one journal entry, with the balances after it
    void customerPosting(EntryType type, UUID journalEntryId, Account account, long amountMinor, String currency,
                         String reference) {
        EventMetadata metadata = OutboxWriter.newMetadata();
        LedgerPostingEvent event = LedgerPostingEvent.newBuilder()
                .setMetadata(metadata)
                .setAccountId(account.getId().toString())
                .setUserId(account.getUserId().toString())
                .setJournalEntryId(journalEntryId.toString())
                .setEntryType(toProto(type))
                .setDirection(amountMinor > 0 ? PostingDirection.POSTING_DIRECTION_CREDIT
                        : PostingDirection.POSTING_DIRECTION_DEBIT)
                .setAmount(money(Math.abs(amountMinor), currency))
                .setLedgerBalanceAfter(money(account.getLedgerBalance(), currency))
                .setAvailableBalanceAfter(money(account.getAvailableBalance(), currency))
                .setReference(reference)
                .build();
        outbox.write(Topics.LEDGER_POSTINGS, account.getId().toString(), "LedgerPosting." + type, metadata, event);
    }

    private static Money money(long amountMinor, String currency) {
        return Money.newBuilder().setAmountMinor(amountMinor).setCurrency(currency).build();
    }

    private static com.cardplatform.contracts.events.v1.EntryType toProto(EntryType type) {
        return switch (type) {
            case TOP_UP -> com.cardplatform.contracts.events.v1.EntryType.ENTRY_TYPE_TOP_UP;
            case CARD_CAPTURE -> com.cardplatform.contracts.events.v1.EntryType.ENTRY_TYPE_CARD_CAPTURE;
            case CARD_REFUND -> com.cardplatform.contracts.events.v1.EntryType.ENTRY_TYPE_CARD_REFUND;
        };
    }
}