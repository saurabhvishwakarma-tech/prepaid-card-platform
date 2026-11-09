package com.cardplatform.core.ledger.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

// One side of a journal entry: positive credits the account, negative debits it.
@Entity
@Table(schema = "ledger", name = "postings")
public class Posting {

    @Id
    private UUID id;

    private UUID journalEntryId;

    private UUID accountId;

    private long amount;

    private String currency;

    private Instant createdAt;

    protected Posting() {
    }

    public static Posting create(UUID journalEntryId, UUID accountId, long amount, String currency) {
        Posting posting = new Posting();
        posting.id = UUID.randomUUID();
        posting.journalEntryId = journalEntryId;
        posting.accountId = accountId;
        posting.amount = amount;
        posting.currency = currency;
        posting.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return posting;
    }

    public long getAmount() {
        return amount;
    }
}