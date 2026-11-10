
package com.cardplatform.core.payments.model;

import com.cardplatform.core.card.api.MerchantCategory;
import com.cardplatform.core.payments.dto.AuthorizationOutcome;
import com.cardplatform.core.payments.dto.AuthorizeCommand;
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
@Table(schema = "payments", name = "authorizations")
public class Authorization {

    @Id
    private UUID id;

    private String networkTxnId;

    private UUID cardId;

    private UUID accountId;

    private long amountMinor;

    private String currency;

    private String merchantId;

    private String merchantName;

    private String mcc;

    private String merchantCountry;

    @Enumerated(EnumType.STRING)
    private MerchantCategory category;

    @Enumerated(EnumType.STRING)
    private AuthorizationStatus status;

    @Enumerated(EnumType.STRING)
    private PaymentDeclineReason declineReason;

    private String authCode;

    private long clearedAmountMinor;

    private long refundedAmountMinor;

    private Instant createdAt;

    private Instant updatedAt;

    @Version
    private Long version;

    protected Authorization() {
    }

    public static Authorization approved(AuthorizeCommand command, UUID cardId, UUID accountId,
                                         MerchantCategory category, String authCode) {
        Authorization authorization = create(command, cardId, accountId, category);
        authorization.status = AuthorizationStatus.APPROVED;
        authorization.authCode = authCode;
        return authorization;
    }

    // cardId and accountId are null when the card number matched no card
    public static Authorization declined(AuthorizeCommand command, UUID cardId, UUID accountId,
                                         MerchantCategory category, PaymentDeclineReason reason) {
        Authorization authorization = create(command, cardId, accountId, category);
        authorization.status = AuthorizationStatus.DECLINED;
        authorization.declineReason = reason;
        return authorization;
    }

    private static Authorization create(AuthorizeCommand command, UUID cardId, UUID accountId,
                                        MerchantCategory category) {
        Authorization authorization = new Authorization();
        authorization.id = UUID.randomUUID();
        authorization.networkTxnId = command.networkTxnId();
        authorization.cardId = cardId;
        authorization.accountId = accountId;
        authorization.amountMinor = command.amount().amountMinor();
        authorization.currency = command.amount().currency();
        authorization.merchantId = command.merchantId();
        authorization.merchantName = command.merchantName();
        authorization.mcc = command.mcc();
        authorization.merchantCountry = command.merchantCountry();
        authorization.category = category;
        authorization.createdAt = now();
        authorization.updatedAt = authorization.createdAt;
        return authorization;
    }

    // What the network was told; a retry of the same request gets exactly this back
    public AuthorizationOutcome outcome() {
        return status == AuthorizationStatus.DECLINED
                ? AuthorizationOutcome.declined(declineReason)
                : AuthorizationOutcome.approved(authCode);
    }

    public void markCleared(long clearedAmount) {
        clearedAmountMinor = clearedAmount;
        status = AuthorizationStatus.CLEARED;
        updatedAt = now();
    }

    public void markReversed() {
        status = AuthorizationStatus.REVERSED;
        updatedAt = now();
    }

    public void addRefund(long amount) {
        refundedAmountMinor += amount;
        updatedAt = now();
    }

    public long refundableAmount() {
        return clearedAmountMinor - refundedAmountMinor;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public String getNetworkTxnId() {
        return networkTxnId;
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

    public AuthorizationStatus getStatus() {
        return status;
    }

    public PaymentDeclineReason getDeclineReason() {
        return declineReason;
    }
}