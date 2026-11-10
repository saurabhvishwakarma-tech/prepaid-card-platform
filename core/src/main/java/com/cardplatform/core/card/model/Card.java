package com.cardplatform.core.card.model;

import com.cardplatform.core.card.api.MerchantCategory;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(schema = "card", name = "cards")
public class Card {

    @Id
    private UUID id;

    private UUID userId;

    private UUID accountId;

    private byte[] panCiphertext;

    private String panHash;

    private String lastFour;

    private int expiryMonth;

    private int expiryYear;

    @Enumerated(EnumType.STRING)
    private CardStatus status;

    private long perTransactionLimitMinor;

    // Small set, always needed with the card, so loaded eagerly
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(schema = "card", name = "card_blocked_categories", joinColumns = @JoinColumn(name = "card_id"))
    @Column(name = "category")
    @Enumerated(EnumType.STRING)
    private Set<MerchantCategory> blockedCategories = new HashSet<>();

    private Instant createdAt;

    private Instant updatedAt;

    @Version
    private Long version;

    protected Card() {
    }

    public static Card issue(UUID id, UUID userId, UUID accountId, byte[] panCiphertext, String panHash,
                             String lastFour, YearMonth expiry, long perTransactionLimitMinor) {
        Card card = new Card();
        card.id = id;
        card.userId = userId;
        card.accountId = accountId;
        card.panCiphertext = panCiphertext;
        card.panHash = panHash;
        card.lastFour = lastFour;
        card.expiryMonth = expiry.getMonthValue();
        card.expiryYear = expiry.getYear();
        card.status = CardStatus.ACTIVE;
        card.perTransactionLimitMinor = perTransactionLimitMinor;
        card.createdAt = now();
        card.updatedAt = card.createdAt;
        return card;
    }

    public void freeze() {
        changeStatus(CardStatus.FROZEN);
    }

    public void unfreeze() {
        changeStatus(CardStatus.ACTIVE);
    }

    public void cancel() {
        changeStatus(CardStatus.CANCELLED);
    }

    public void updateControls(long perTransactionLimitMinor, Set<MerchantCategory> blockedCategories) {
        this.perTransactionLimitMinor = perTransactionLimitMinor;
        // Change the existing collection, not replace it, so Hibernate tracks the rows correctly
        this.blockedCategories.clear();
        this.blockedCategories.addAll(blockedCategories);
        updatedAt = now();
    }

    // A card is valid until the end of its expiry month
    public boolean isExpiredAt(YearMonth month) {
        return expiry().isBefore(month);
    }

    public YearMonth expiry() {
        return YearMonth.of(expiryYear, expiryMonth);
    }

    private void changeStatus(CardStatus newStatus) {
        status = newStatus;
        updatedAt = now();
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

    public byte[] getPanCiphertext() {
        return panCiphertext;
    }

    public String getLastFour() {
        return lastFour;
    }

    public int getExpiryMonth() {
        return expiryMonth;
    }

    public int getExpiryYear() {
        return expiryYear;
    }

    public CardStatus getStatus() {
        return status;
    }

    public long getPerTransactionLimitMinor() {
        return perTransactionLimitMinor;
    }

    public Set<MerchantCategory> getBlockedCategories() {
        return Collections.unmodifiableSet(blockedCategories);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}