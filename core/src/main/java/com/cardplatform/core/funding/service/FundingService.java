package com.cardplatform.core.funding.service;

import com.cardplatform.core.funding.exception.FundingRuleException;
import com.cardplatform.core.funding.exception.PaymentDeclinedException;
import com.cardplatform.core.funding.exception.PaymentProviderUnavailableException;
import com.cardplatform.core.funding.exception.TopUpNotFoundException;
import com.cardplatform.core.funding.model.StripeEvent;
import com.cardplatform.core.funding.model.TopUp;
import com.cardplatform.core.funding.model.TopUpStatus;
import com.cardplatform.core.funding.provider.PaymentProvider;
import com.cardplatform.core.funding.provider.ProviderEvent;
import com.cardplatform.core.funding.provider.ProviderPayment;
import com.cardplatform.core.funding.repository.StripeEventRepository;
import com.cardplatform.core.funding.repository.TopUpRepository;
import com.cardplatform.core.identity.api.IdentityApi;
import com.cardplatform.core.identity.api.UserSummary;
import com.cardplatform.core.ledger.api.AccountView;
import com.cardplatform.core.ledger.api.LedgerApi;
import com.cardplatform.core.shared.money.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class FundingService {

    private static final Logger log = LoggerFactory.getLogger(FundingService.class);

    // The ledger refuses a second entry with the same reference, so a top-up can only ever be credited once
    private static final String LEDGER_REFERENCE_PREFIX = "topup-";

    private final TopUpRepository topUps;
    private final StripeEventRepository stripeEvents;
    private final PaymentProvider provider;
    private final IdentityApi identity;
    private final LedgerApi ledger;
    private final TransactionTemplate transactions;

    public FundingService(TopUpRepository topUps, StripeEventRepository stripeEvents, PaymentProvider provider,
                          IdentityApi identity, LedgerApi ledger, TransactionTemplate transactions) {
        this.topUps = topUps;
        this.stripeEvents = stripeEvents;
        this.provider = provider;
        this.identity = identity;
        this.ledger = ledger;
        this.transactions = transactions;
    }

    // The Stripe call happens outside any database transaction, so a slow Stripe never holds a row lock
    public TopUp startTopUp(UUID userId, String idempotencyKey, Money amount, String paymentMethodId) {
        TopUp topUp = findOrCreate(userId, idempotencyKey, amount, paymentMethodId);
        // Only a top-up that never reached Stripe is sent again; every other retry gets the stored answer
        if (topUp.getStatus() != TopUpStatus.CREATED) {
            return topUp;
        }
        ProviderPayment payment;
        try {
            payment = provider.charge(topUp);
        } catch (PaymentDeclinedException e) {
            log.info("Top-up declined: topUpId={}, code={}", topUp.getId(), e.getCode());
            return update(topUp.getId(), t -> t.fail(e.getCode()));
        }
        if (payment.needsCustomerAction()) {
            // There's no page to show a 3-D Secure challenge on, so a card that demands one can't be used here
            return update(topUp.getId(), t -> t.fail("authentication_required"));
        }
        String paymentId = payment.id();
        log.info("Top-up sent to Stripe: topUpId={}, paymentId={}, status={}", topUp.getId(), paymentId,
                payment.status());
        return update(topUp.getId(), t -> t.submitted(paymentId));
    }

    @Transactional(readOnly = true)
    public TopUp getTopUp(UUID userId, UUID topUpId) {
        return topUps.findByIdAndUserId(topUpId, userId).orElseThrow(TopUpNotFoundException::new);
    }

    public void handleWebhook(ProviderEvent event) {
        try {
            transactions.executeWithoutResult(status -> applyOnce(event));
        } catch (DataIntegrityViolationException e) {
            // The same event delivered twice at the same moment: the other delivery recorded and applied it
            if (!stripeEvents.existsById(event.id())) {
                throw e;
            }
        }
    }

    // For top-ups whose webhook never came (core was down, the CLI wasn't running): ask Stripe directly
    public int reconcilePendingBefore(Instant cutoff) {
        int settled = 0;
        for (TopUp pending : topUps.findTop50ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(TopUpStatus.PENDING, cutoff)) {
            try {
                if (settle(pending.getId(), provider.retrieve(pending.getPaymentIntentId()))) {
                    settled++;
                }
            } catch (PaymentProviderUnavailableException e) {
                log.warn("Reconciliation paused: Stripe unreachable");
                return settled;
            }
        }
        return settled;
    }

    private boolean settle(UUID topUpId, ProviderPayment payment) {
        if (payment.succeeded()) {
            transactions.executeWithoutResult(status -> complete(topUpId, payment));
            return true;
        }
        if (payment.failed()) {
            transactions.executeWithoutResult(status -> fail(topUpId));
            return true;
        }
        return false;
    }

    private void applyOnce(ProviderEvent event) {
        if (stripeEvents.existsById(event.id())) {
            log.info("Duplicate Stripe event ignored: eventId={}", event.id());
            return;
        }
        stripeEvents.saveAndFlush(StripeEvent.received(event.id(), event.type()));
        switch (event.type()) {
            case ProviderEvent.PAYMENT_SUCCEEDED -> complete(event.payment().topUpId(), event.payment());
            case ProviderEvent.PAYMENT_FAILED -> fail(event.payment().topUpId());
            default -> log.debug("Stripe event not used: eventId={}, type={}", event.id(), event.type());
        }
    }

    private void complete(UUID topUpId, ProviderPayment payment) {
        Optional<TopUp> found = topUpId == null ? Optional.empty() : topUps.findByIdForUpdate(topUpId);
        if (found.isEmpty()) {
            log.warn("Stripe payment for no known top-up ignored: paymentId={}", payment.id());
            return;
        }
        TopUp topUp = found.get();
        if (topUp.getStatus() == TopUpStatus.COMPLETED) {
            return;
        }
        if (payment.amountMinor() != topUp.getAmountMinor() || !payment.currency().equals(topUp.getCurrency())) {
            // Never credit an amount other than the one asked for; this needs a person to look at it
            log.error("Stripe amount differs from top-up, not credited: topUpId={}, paymentId={}",
                    topUp.getId(), payment.id());
            return;
        }
        // Money was taken, so it is credited even if the top-up had been marked failed
        ledger.creditTopUp(topUp.getAccountId(), LEDGER_REFERENCE_PREFIX + topUp.getId(), topUp.amount());
        topUp.complete(payment.id());
        log.info("Top-up completed: topUpId={}, accountId={}, amountMinor={}",
                topUp.getId(), topUp.getAccountId(), topUp.getAmountMinor());
    }

    private void fail(UUID topUpId) {
        if (topUpId != null) {
            topUps.findByIdForUpdate(topUpId).ifPresent(topUp -> topUp.fail("payment_failed"));
        }
    }

    private TopUp findOrCreate(UUID userId, String idempotencyKey, Money amount, String paymentMethodId) {
        try {
            return transactions.execute(status -> topUps.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                    .map(existing -> requireSameRequest(existing, amount, paymentMethodId))
                    .orElseGet(() -> topUps.saveAndFlush(newTopUp(userId, idempotencyKey, amount, paymentMethodId))));
        } catch (DataIntegrityViolationException e) {
            // Two requests with one key arrived together and this one lost on the unique constraint
            return topUps.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                    .map(existing -> requireSameRequest(existing, amount, paymentMethodId))
                    .orElseThrow(() -> e);
        }
    }

    private TopUp newTopUp(UUID userId, String idempotencyKey, Money amount, String paymentMethodId) {
        boolean verified = identity.findUser(userId).map(UserSummary::kycVerified).orElse(false);
        if (!verified) {
            throw new FundingRuleException("KYC_REQUIRED", "Identity must be verified before topping up");
        }
        UUID accountId = ledger.findCustomerAccount(userId)
                .map(AccountView::accountId)
                .orElseThrow(() -> new FundingRuleException("ACCOUNT_NOT_FOUND", "No account to top up"));
        return TopUp.start(userId, accountId, idempotencyKey, amount, paymentMethodId);
    }

    private static TopUp requireSameRequest(TopUp existing, Money amount, String paymentMethodId) {
        if (!existing.sameRequest(amount, paymentMethodId)) {
            throw new FundingRuleException("IDEMPOTENCY_KEY_REUSED",
                    "This Idempotency-Key was already used for a different top-up");
        }
        return existing;
    }

    private TopUp update(UUID topUpId, Consumer<TopUp> change) {
        return transactions.execute(status -> {
            TopUp topUp = topUps.findByIdForUpdate(topUpId).orElseThrow(TopUpNotFoundException::new);
            change.accept(topUp);
            return topUp;
        });
    }
}