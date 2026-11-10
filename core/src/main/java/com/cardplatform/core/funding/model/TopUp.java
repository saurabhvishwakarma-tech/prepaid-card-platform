package com.cardplatform.core.funding.model;

import com.cardplatform.core.shared.money.Money;
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
@Table(schema = "funding", name = "top_ups")
public class TopUp {

    @Id
    private UUID id;

    private UUID userId;

    private UUID accountId;

    private String idempotencyKey;

    private long amountMinor;

    private String currency;

    private String paymentMethodId;

    @Enumerated(EnumType.STRING)
    private TopUpStatus status;

    private String paymentIntentId;

    private String failureCode;

    private Instant createdAt;

    private Instant updatedAt;

    @Version
    private Long version;

    protected TopUp() {
    }

    public static TopUp start(UUID userId, UUID accountId, String idempotencyKey, Money amount,
                              String paymentMethodId) {
        TopUp topUp = new TopUp();
        topUp.id = UUID.randomUUID();
        topUp.userId = userId;
        topUp.accountId = accountId;
        topUp.idempotencyKey = idempotencyKey;
        topUp.amountMinor = amount.amountMinor();
        topUp.currency = amount.currency();
        topUp.paymentMethodId = paymentMethodId;
        topUp.status = TopUpStatus.CREATED;
        topUp.createdAt = now();
        topUp.updatedAt = topUp.createdAt;
        return topUp;
    }

    // A retry must ask for exactly the same thing; anything else is the key being reused by mistake
    public boolean sameRequest(Money amount, String paymentMethodId) {
        return amountMinor == amount.amountMinor()
                && currency.equals(amount.currency())
                && this.paymentMethodId.equals(paymentMethodId);
    }

    // The webhook can arrive before this, in which case the top-up is already completed and stays so
    public void submitted(String paymentIntentId) {
        if (this.paymentIntentId == null) {
            this.paymentIntentId = paymentIntentId;
        }
        if (status == TopUpStatus.CREATED) {
            status = TopUpStatus.PENDING;
        }
        updatedAt = now();
    }

    public void complete(String paymentIntentId) {
        this.paymentIntentId = paymentIntentId;
        status = TopUpStatus.COMPLETED;
        failureCode = null;
        updatedAt = now();
    }

    public void fail(String code) {
        if (status == TopUpStatus.CREATED || status == TopUpStatus.PENDING) {
            status = TopUpStatus.FAILED;
            failureCode = code;
            updatedAt = now();
        }
    }

    public Money amount() {
        return new Money(amountMinor, currency);
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public String getPaymentMethodId() {
        return paymentMethodId;
    }

    public TopUpStatus getStatus() {
        return status;
    }

    public String getPaymentIntentId() {
        return paymentIntentId;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}