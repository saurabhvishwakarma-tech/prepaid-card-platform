package com.cardplatform.core.ledger.model;

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
@Table(schema = "ledger", name = "accounts")
public class Account {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private AccountOwnerType ownerType;

    private UUID userId;

    private String code;

    private String currency;

    private long ledgerBalance;

    private long availableBalance;

    private Instant createdAt;

    private Instant updatedAt;

    @Version
    private Long version;

    protected Account() {
    }

    public static Account openCustomer(UUID userId, String currency) {
        Account account = new Account();
        account.id = UUID.randomUUID();
        account.ownerType = AccountOwnerType.CUSTOMER;
        account.userId = userId;
        account.currency = currency;
        account.createdAt = now();
        account.updatedAt = account.createdAt;
        return account;
    }

    // A hold: the money stays on the ledger but can no longer be spent
    public void reserve(long amount) {
        availableBalance -= amount;
        updatedAt = now();
    }

    public void unreserve(long amount) {
        availableBalance += amount;
        updatedAt = now();
    }

    // A posting is money actually moving, so both balances change
    public void post(long amount) {
        ledgerBalance += amount;
        availableBalance += amount;
        updatedAt = now();
    }

    public boolean isCustomer() {
        return ownerType == AccountOwnerType.CUSTOMER;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getCurrency() {
        return currency;
    }

    public long getLedgerBalance() {
        return ledgerBalance;
    }

    public long getAvailableBalance() {
        return availableBalance;
    }
}