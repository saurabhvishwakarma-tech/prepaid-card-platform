package com.cardplatform.core.payments.service;

import com.cardplatform.core.card.api.CardApi;
import com.cardplatform.core.card.api.CardCheck;
import com.cardplatform.core.card.api.CardDeclineReason;
import com.cardplatform.core.ledger.api.HoldResult;
import com.cardplatform.core.ledger.api.LedgerApi;
import com.cardplatform.core.payments.dto.AuthorizationOutcome;
import com.cardplatform.core.payments.dto.AuthorizeCommand;
import com.cardplatform.core.payments.dto.ClearCommand;
import com.cardplatform.core.payments.dto.OperationOutcome;
import com.cardplatform.core.payments.dto.RefundCommand;
import com.cardplatform.core.payments.dto.ReverseCommand;
import com.cardplatform.core.payments.model.Authorization;
import com.cardplatform.core.payments.model.AuthorizationStatus;
import com.cardplatform.core.payments.model.NetworkOperation;
import com.cardplatform.core.payments.model.OperationRejectReason;
import com.cardplatform.core.payments.model.OperationType;
import com.cardplatform.core.payments.model.PaymentDeclineReason;
import com.cardplatform.core.payments.repository.AuthorizationRepository;
import com.cardplatform.core.payments.repository.NetworkOperationRepository;
import com.cardplatform.core.shared.money.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.function.Function;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    // No 0/O or 1/I, so auth codes can't be misread
    private static final String AUTH_CODE_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int AUTH_CODE_LENGTH = 6;
    private static final int MAX_ID_LENGTH = 64;

    private final AuthorizationRepository authorizations;
    private final NetworkOperationRepository operations;
    private final CardApi cards;
    private final LedgerApi ledger;
    private final PaymentEventPublisher events;
    private final TransactionTemplate transactions;
    private final SecureRandom random = new SecureRandom();

    public PaymentService(AuthorizationRepository authorizations, NetworkOperationRepository operations,
                          CardApi cards, LedgerApi ledger, PaymentEventPublisher events,
                          TransactionTemplate transactions) {
        this.authorizations = authorizations;
        this.operations = operations;
        this.cards = cards;
        this.ledger = ledger;
        this.events = events;
        this.transactions = transactions;
    }

    public AuthorizationOutcome authorize(AuthorizeCommand command) {
        validate(command);
        try {
            return transactions.execute(status -> authorizeOnce(command));
        } catch (DataIntegrityViolationException e) {
            // Two copies of one request arrived together and this one lost on the unique constraint:
            // the winner's committed answer is the answer
            return authorizations.findByNetworkTxnId(command.networkTxnId())
                    .map(Authorization::outcome)
                    .orElseThrow(() -> e);
        }
    }

    public OperationOutcome clear(ClearCommand command) {
        requireId(command.clearingId(), "clearingId");
        requireId(command.networkTxnId(), "networkTxnId");
        requirePositive(command.amount());
        return runOnce(OperationType.CLEAR, command.clearingId(), command.networkTxnId(),
                command.amount().amountMinor(), authorization -> {
                    if (authorization.getStatus() != AuthorizationStatus.APPROVED) {
                        return OperationOutcome.rejected(OperationRejectReason.INVALID_STATE);
                    }
                    if (!authorization.getCurrency().equals(command.amount().currency())) {
                        return OperationOutcome.rejected(OperationRejectReason.CURRENCY_MISMATCH);
                    }
                    // Clearing can be lower than authorised (the rest is released) but never higher
                    if (command.amount().amountMinor() > authorization.getAmountMinor()) {
                        return OperationOutcome.rejected(OperationRejectReason.AMOUNT_EXCEEDS_ALLOWED);
                    }
                    ledger.captureHold(authorization.getNetworkTxnId(), command.amount());
                    authorization.markCleared(command.amount().amountMinor());
                    events.cleared(authorization, command.clearingId(), command.amount().amountMinor());
                    return OperationOutcome.ACCEPTED;
                });
    }

    public OperationOutcome reverse(ReverseCommand command) {
        requireId(command.reversalId(), "reversalId");
        requireId(command.networkTxnId(), "networkTxnId");
        return runOnce(OperationType.REVERSE, command.reversalId(), command.networkTxnId(), null, authorization -> {
            // Only an uncleared approval can be reversed; after clearing, money comes back as a refund
            if (authorization.getStatus() != AuthorizationStatus.APPROVED) {
                return OperationOutcome.rejected(OperationRejectReason.INVALID_STATE);
            }
            ledger.releaseHold(authorization.getNetworkTxnId());
            authorization.markReversed();
            events.reversed(authorization, command.reversalId());
            return OperationOutcome.ACCEPTED;
        });
    }

    public OperationOutcome refund(RefundCommand command) {
        requireId(command.refundId(), "refundId");
        requireId(command.networkTxnId(), "networkTxnId");
        requirePositive(command.amount());
        return runOnce(OperationType.REFUND, command.refundId(), command.networkTxnId(),
                command.amount().amountMinor(), authorization -> {
                    if (authorization.getStatus() != AuthorizationStatus.CLEARED) {
                        return OperationOutcome.rejected(OperationRejectReason.INVALID_STATE);
                    }
                    if (!authorization.getCurrency().equals(command.amount().currency())) {
                        return OperationOutcome.rejected(OperationRejectReason.CURRENCY_MISMATCH);
                    }
                    if (command.amount().amountMinor() > authorization.refundableAmount()) {
                        return OperationOutcome.rejected(OperationRejectReason.AMOUNT_EXCEEDS_ALLOWED);
                    }
                    ledger.creditRefund(authorization.getAccountId(), command.refundId(), command.amount());
                    authorization.addRefund(command.amount().amountMinor());
                    events.refunded(authorization, command.refundId(), command.amount().amountMinor());
                    return OperationOutcome.ACCEPTED;
                });
    }


    // Approvals the network never cleared or reversed are released after the TTL, as card issuers do.
    // This also frees holds left behind by a reversal that arrived before its authorisation was committed.
    public int expireAuthorizationsBefore(Instant cutoff) {
        int expired = 0;
        for (Authorization stale : authorizations.findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                AuthorizationStatus.APPROVED, cutoff)) {
            Boolean released = transactions.execute(status -> authorizations
                    .findByNetworkTxnIdForUpdate(stale.getNetworkTxnId())
                    // Checked again under the lock: a clearing may have arrived since the list was read
                    .filter(authorization -> authorization.getStatus() == AuthorizationStatus.APPROVED)
                    .map(this::expire)
                    .orElse(false));
            if (Boolean.TRUE.equals(released)) {
                expired++;
            }
        }
        return expired;
    }

    private boolean expire(Authorization authorization) {
        ledger.releaseHold(authorization.getNetworkTxnId());
        authorization.markReversed();
        events.reversed(authorization, "expiry-" + authorization.getNetworkTxnId());
        log.info("Authorization expired: networkTxnId={}, accountId={}, amountMinor={}",
                authorization.getNetworkTxnId(), authorization.getAccountId(), authorization.getAmountMinor());
        return true;
    }

    // Card checks, the ledger hold, the authorisation record and its event commit together or not at all
    private AuthorizationOutcome authorizeOnce(AuthorizeCommand command) {
        Optional<Authorization> existing = authorizations.findByNetworkTxnId(command.networkTxnId());
        if (existing.isPresent()) {
            return existing.get().outcome(); // a network retry: same answer, nothing done twice
        }

        CardCheck check = cards.checkForPayment(command.pan(), command.expiryMonth(), command.expiryYear(),
                command.amount(), command.mcc());
        if (!check.approved()) {
            return save(Authorization.declined(command, check, toPaymentReason(check.declineReason())));
        }

        HoldResult hold = ledger.placeHold(check.accountId(), command.networkTxnId(), command.amount());
        if (hold == HoldResult.INSUFFICIENT_FUNDS) {
            return save(Authorization.declined(command, check, PaymentDeclineReason.INSUFFICIENT_FUNDS));
        }
        return save(Authorization.approved(command, check, newAuthCode()));
    }

    private AuthorizationOutcome save(Authorization authorization) {
        authorizations.saveAndFlush(authorization);
        events.authorizationDecided(authorization);
        log.info("Authorization {}: networkTxnId={}, accountId={}, amountMinor={}, declineReason={}",
                authorization.getStatus(), authorization.getNetworkTxnId(), authorization.getAccountId(),
                authorization.getAmountMinor(), authorization.getDeclineReason());
        return authorization.outcome();
    }

    // Shared shape of clear, reverse and refund: answer a retry from the stored record, otherwise
    // lock the authorisation, apply the operation and store its outcome, all in one transaction
    private OperationOutcome runOnce(OperationType type, String operationId, String networkTxnId,
                                     Long amountMinor, Function<Authorization, OperationOutcome> operation) {
        try {
            return transactions.execute(status -> {
                Optional<NetworkOperation> existing = operations.findByOperationTypeAndOperationId(type, operationId);
                if (existing.isPresent()) {
                    return existing.get().outcome();
                }
                OperationOutcome outcome = authorizations.findByNetworkTxnIdForUpdate(networkTxnId)
                        .map(operation)
                        .orElseGet(() -> OperationOutcome.rejected(OperationRejectReason.AUTHORIZATION_NOT_FOUND));
                operations.saveAndFlush(NetworkOperation.record(type, operationId, networkTxnId, amountMinor, outcome));
                log.info("{} {}: operationId={}, networkTxnId={}, rejectReason={}", type,
                        outcome.accepted() ? "accepted" : "rejected", operationId, networkTxnId, outcome.rejectReason());
                return outcome;
            });
        } catch (DataIntegrityViolationException e) {
            return operations.findByOperationTypeAndOperationId(type, operationId)
                    .map(NetworkOperation::outcome)
                    .orElseThrow(() -> e);
        }
    }

    // Exhaustive switch: adding a card decline reason without mapping it here fails to compile
    private static PaymentDeclineReason toPaymentReason(CardDeclineReason reason) {
        return switch (reason) {
            case CARD_NOT_FOUND -> PaymentDeclineReason.CARD_NOT_FOUND;
            case CARD_NOT_ACTIVE -> PaymentDeclineReason.CARD_NOT_ACTIVE;
            case CARD_EXPIRED -> PaymentDeclineReason.CARD_EXPIRED;
            case TRANSACTION_LIMIT_EXCEEDED -> PaymentDeclineReason.TRANSACTION_LIMIT_EXCEEDED;
            case MERCHANT_CATEGORY_BLOCKED -> PaymentDeclineReason.MERCHANT_CATEGORY_BLOCKED;
            case CURRENCY_NOT_SUPPORTED -> PaymentDeclineReason.CURRENCY_NOT_SUPPORTED;
        };
    }

    private String newAuthCode() {
        StringBuilder code = new StringBuilder(AUTH_CODE_LENGTH);
        for (int i = 0; i < AUTH_CODE_LENGTH; i++) {
            code.append(AUTH_CODE_CHARACTERS.charAt(random.nextInt(AUTH_CODE_CHARACTERS.length())));
        }
        return code.toString();
    }

    // Messages name the bad field but never echo card data back
    private static void validate(AuthorizeCommand command) {
        requireId(command.networkTxnId(), "networkTxnId");
        if (command.pan() == null || command.pan().isBlank()) {
            throw new IllegalArgumentException("pan is required");
        }
        if (command.expiryMonth() < 1 || command.expiryMonth() > 12) {
            throw new IllegalArgumentException("expiryMonth must be 1-12");
        }
        requirePositive(command.amount());
        requireText(command.merchantId(), "merchant.merchantId", MAX_ID_LENGTH);
        requireText(command.merchantName(), "merchant.name", 200);
        if (command.mcc() == null || !command.mcc().matches("\\d{4}")) {
            throw new IllegalArgumentException("merchant.mcc must be four digits");
        }
        if (command.merchantCountry() == null || !command.merchantCountry().matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("merchant.country must be a two-letter code");
        }
    }

    private static void requireId(String value, String field) {
        requireText(value, field, MAX_ID_LENGTH);
    }

    private static void requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be 1-" + maxLength + " characters");
        }
    }

    private static void requirePositive(Money amount) {
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
    }
}