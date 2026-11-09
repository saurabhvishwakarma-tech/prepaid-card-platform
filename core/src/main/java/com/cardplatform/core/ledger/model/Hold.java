package com.cardplatform.core.ledger.model;

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
@Table(schema = "ledger", name = "holds")
public class Hold {

    @Id
    private UUID id;

    private UUID accountId;

    private String reference;

    private long amount;

    private String currency;

    @Enumerated(EnumType.STRING)
    private HoldStatus status;

    private Instant createdAt;

    private Instant updatedAt;

    @Version
    private Long version;

    protected Hold() {
    }

    public static Hold place(UUID accountId, String reference, Money amount) {
        Hold hold = new Hold();
        hold.id = UUID.randomUUID();
        hold.accountId = accountId;
        hold.reference = reference;
        hold.amount = amount.amountMinor();
        hold.currency = amount.currency();
        hold.status = HoldStatus.ACTIVE;
        hold.createdAt = now();
        hold.updatedAt = hold.createdAt;
        return hold;
    }

    public boolean matches(UUID accountId, Money amount) {
        return this.accountId.equals(accountId)
                && this.amount == amount.amountMinor()
                && this.currency.equals(amount.currency());
    }

    public void capture() {
        status = HoldStatus.CAPTURED;
        updatedAt = now();
    }

    public void release() {
        status = HoldStatus.RELEASED;
        updatedAt = now();
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getAccountId() {
        return accountId;
    }

    public long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public HoldStatus getStatus() {
        return status;
    }
}