package com.cardplatform.core.payments.service;

import com.cardplatform.contracts.common.v1.EventMetadata;
import com.cardplatform.contracts.common.v1.Merchant;
import com.cardplatform.contracts.common.v1.Money;
import com.cardplatform.contracts.events.v1.PaymentAuthorized;
import com.cardplatform.contracts.events.v1.PaymentCleared;
import com.cardplatform.contracts.events.v1.PaymentDeclined;
import com.cardplatform.contracts.events.v1.PaymentEvent;
import com.cardplatform.contracts.events.v1.PaymentRefunded;
import com.cardplatform.contracts.events.v1.PaymentReversed;
import com.cardplatform.core.card.api.MerchantCategory;
import com.cardplatform.core.payments.mapper.NetworkMessageMapper;
import com.cardplatform.core.payments.model.Authorization;
import com.cardplatform.core.payments.model.AuthorizationStatus;
import com.cardplatform.core.shared.outbox.OutboxWriter;
import com.cardplatform.core.shared.outbox.Topics;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

// Turns payment changes into PaymentEvents in the outbox, inside the same transaction as the change
@Component
class PaymentEventPublisher {

    private final OutboxWriter outbox;

    PaymentEventPublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    void authorizationDecided(Authorization authorization) {
        // A decline for an unknown card has no account to tell
        if (authorization.getAccountId() == null) {
            return;
        }
        Money amount = money(authorization.getAmountMinor(), authorization.getCurrency());
        if (authorization.getStatus() == AuthorizationStatus.DECLINED) {
            publish(authorization, "PaymentDeclined", event -> event.setDeclined(PaymentDeclined.newBuilder()
                    .setAmount(amount)
                    .setReason(NetworkMessageMapper.toProto(authorization.getDeclineReason()))));
        } else {
            publish(authorization, "PaymentAuthorized", event -> event.setAuthorized(PaymentAuthorized.newBuilder()
                    .setAmount(amount)
                    .setAuthCode(authorization.getAuthCode())));
        }
    }

    void cleared(Authorization authorization, String clearingId, long clearedMinor) {
        String currency = authorization.getCurrency();
        publish(authorization, "PaymentCleared", event -> event.setCleared(PaymentCleared.newBuilder()
                .setClearingId(clearingId)
                .setClearedAmount(money(clearedMinor, currency))
                .setReleasedAmount(money(authorization.getAmountMinor() - clearedMinor, currency))));
    }

    void reversed(Authorization authorization, String reversalId) {
        publish(authorization, "PaymentReversed", event -> event.setReversed(PaymentReversed.newBuilder()
                .setReversalId(reversalId)
                .setReleasedAmount(money(authorization.getAmountMinor(), authorization.getCurrency()))));
    }

    void refunded(Authorization authorization, String refundId, long refundMinor) {
        publish(authorization, "PaymentRefunded", event -> event.setRefunded(PaymentRefunded.newBuilder()
                .setRefundId(refundId)
                .setAmount(money(refundMinor, authorization.getCurrency()))));
    }

    private void publish(Authorization authorization, String eventType, Consumer<PaymentEvent.Builder> details) {
        EventMetadata metadata = OutboxWriter.newMetadata();
        PaymentEvent.Builder event = PaymentEvent.newBuilder()
                .setMetadata(metadata)
                .setAccountId(authorization.getAccountId().toString())
                .setCardId(authorization.getCardId().toString())
                .setNetworkTxnId(authorization.getNetworkTxnId())
                .setMerchant(Merchant.newBuilder()
                        .setMerchantId(authorization.getMerchantId())
                        .setName(authorization.getMerchantName())
                        .setMcc(authorization.getMcc())
                        .setCountry(authorization.getMerchantCountry()))
                .setCategory(toProto(authorization.getCategory()));
        // Authorisations created before V5 have no user recorded
        if (authorization.getUserId() != null) {
            event.setUserId(authorization.getUserId().toString());
        }
        details.accept(event);
        // Keyed by account: every event for one account lands on one partition, in order
        outbox.write(Topics.PAYMENT_EVENTS, authorization.getAccountId().toString(), eventType, metadata, event.build());
    }

    private static Money money(long amountMinor, String currency) {
        return Money.newBuilder().setAmountMinor(amountMinor).setCurrency(currency).build();
    }

    // Exhaustive switch: a new category without a mapping fails to compile
    private static com.cardplatform.contracts.common.v1.MerchantCategory toProto(MerchantCategory category) {
        return switch (category) {
            case GROCERIES -> com.cardplatform.contracts.common.v1.MerchantCategory.MERCHANT_CATEGORY_GROCERIES;
            case RESTAURANTS -> com.cardplatform.contracts.common.v1.MerchantCategory.MERCHANT_CATEGORY_RESTAURANTS;
            case TRANSPORT -> com.cardplatform.contracts.common.v1.MerchantCategory.MERCHANT_CATEGORY_TRANSPORT;
            case TRAVEL -> com.cardplatform.contracts.common.v1.MerchantCategory.MERCHANT_CATEGORY_TRAVEL;
            case ENTERTAINMENT -> com.cardplatform.contracts.common.v1.MerchantCategory.MERCHANT_CATEGORY_ENTERTAINMENT;
            case RETAIL -> com.cardplatform.contracts.common.v1.MerchantCategory.MERCHANT_CATEGORY_RETAIL;
            case DIGITAL_SERVICES -> com.cardplatform.contracts.common.v1.MerchantCategory.MERCHANT_CATEGORY_DIGITAL_SERVICES;
            case GAMBLING -> com.cardplatform.contracts.common.v1.MerchantCategory.MERCHANT_CATEGORY_GAMBLING;
            case OTHER -> com.cardplatform.contracts.common.v1.MerchantCategory.MERCHANT_CATEGORY_OTHER;
        };
    }
}