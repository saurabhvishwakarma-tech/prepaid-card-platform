package com.cardplatform.history.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    private UUID id;

    private UUID userId;

    private UUID accountId;

    @Enumerated(EnumType.STRING)
    private TransactionType type;

    private String reference;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    private long amountMinor;

    private String currency;

    private String merchantName;

    private String category;

    private String declineReason;

    private Instant occurredAt;

    private Instant updatedAt;

    @Version
    private Long version;

    protected Transaction() {
    }

    public static Transaction create(TransactionType type, String reference, UUID userId, UUID accountId,
                                     TransactionStatus status, long amountMinor, String currency,
                                     String merchantName, String category, String declineReason,
                                     Instant occurredAt) {
        Transaction transaction = new Transaction();
        transaction.id = UUID.randomUUID();
        transaction.type = type;
        transaction.reference = reference;
        transaction.userId = userId;
        transaction.accountId = accountId;
        transaction.status = status;
        transaction.amountMinor = amountMinor;
        transaction.currency = currency;
        transaction.merchantName = merchantName;
        transaction.category = category;
        transaction.declineReason = declineReason;
        transaction.occurredAt = occurredAt.truncatedTo(ChronoUnit.MICROS);
        transaction.updatedAt = transaction.occurredAt;
        return transaction;
    }

    // Only a pending payment moves on; an event arriving late for a finished one changes nothing
    public void complete(long clearedAmountMinor, Instant at) {
        if (status == TransactionStatus.PENDING) {
            amountMinor = clearedAmountMinor;
            status = TransactionStatus.COMPLETED;
            updatedAt = at.truncatedTo(ChronoUnit.MICROS);
        }
    }

    public void reverse(Instant at) {
        if (status == TransactionStatus.PENDING) {
            status = TransactionStatus.REVERSED;
            updatedAt = at.truncatedTo(ChronoUnit.MICROS);
        }
    }

    public UUID getId() {
        return id;
    }

    public TransactionType getType() {
        return type;
    }

    public String getReference() {
        return reference;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public String getCategory() {
        return category;
    }

    public String getDeclineReason() {
        return declineReason;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}