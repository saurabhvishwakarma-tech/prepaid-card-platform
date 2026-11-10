package com.cardplatform.core.card.service;

import com.cardplatform.core.card.api.CardApi;
import com.cardplatform.core.card.api.CardCheck;
import com.cardplatform.core.card.api.CardDeclineReason;
import com.cardplatform.core.card.api.MerchantCategory;
import com.cardplatform.core.card.config.CardProperties;
import com.cardplatform.core.card.dto.CardDetailsResponse;
import com.cardplatform.core.card.dto.UpdateControlsRequest;
import com.cardplatform.core.card.exception.CardNotFoundException;
import com.cardplatform.core.card.exception.CardRuleException;
import com.cardplatform.core.card.mapper.CardMapper;
import com.cardplatform.core.card.model.Card;
import com.cardplatform.core.card.model.CardStatus;
import com.cardplatform.core.card.repository.CardRepository;
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

import java.security.SecureRandom;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Service
public class CardService implements CardApi {

    private static final Logger log = LoggerFactory.getLogger(CardService.class);

    // €1,000 per transaction until the customer changes it
    private static final long DEFAULT_LIMIT_MINOR = 100_000;
    private static final int PAN_LENGTH = 16;
    private static final int MAX_PAN_ATTEMPTS = 5;
    private static final int VALIDITY_YEARS = 3;

    private final CardRepository cards;
    private final CardCrypto crypto;
    private final CardProperties properties;
    private final CardMapper cardMapper;
    private final IdentityApi identity;
    private final LedgerApi ledger;
    private final SecureRandom random = new SecureRandom();

    public CardService(CardRepository cards, CardCrypto crypto, CardProperties properties,
                       CardMapper cardMapper, IdentityApi identity, LedgerApi ledger) {
        this.cards = cards;
        this.crypto = crypto;
        this.properties = properties;
        this.cardMapper = cardMapper;
        this.identity = identity;
        this.ledger = ledger;
    }

    @Transactional
    public Card issue(UUID userId) {
        identity.findUser(userId)
                .filter(UserSummary::kycVerified)
                .orElseThrow(() -> new CardRuleException("KYC_NOT_VERIFIED", "Complete KYC before requesting a card"));
        AccountView account = ledger.findCustomerAccount(userId)
                .orElseThrow(() -> new CardRuleException("ACCOUNT_NOT_FOUND", "No account to link the card to"));
        if (cards.findByUserIdAndStatusNot(userId, CardStatus.CANCELLED).isPresent()) {
            throw new CardRuleException("CARD_ALREADY_EXISTS", "Only one card per user; cancel the current one first");
        }

        String pan = newPan();
        UUID cardId = UUID.randomUUID();
        Card card = Card.issue(cardId, userId, account.accountId(), crypto.encryptPan(pan, cardId),
                crypto.panHash(pan), pan.substring(PAN_LENGTH - 4),
                currentMonth().plusYears(VALIDITY_YEARS), DEFAULT_LIMIT_MINOR);
        try {
            cards.saveAndFlush(card);
        } catch (DataIntegrityViolationException e) {
            // Two requests at the same moment: the partial unique index stops the second
            throw new CardRuleException("CARD_ALREADY_EXISTS", "Only one card per user; cancel the current one first");
        }
        log.info("Card issued: cardId={}, userId={}", cardId, userId);
        return card;
    }

    @Transactional(readOnly = true)
    public Card currentCard(UUID userId) {
        return cards.findByUserIdAndStatusNot(userId, CardStatus.CANCELLED)
                .orElseThrow(CardNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public CardDetailsResponse reveal(UUID userId) {
        Card card = currentCard(userId);
        String pan = crypto.decryptPan(card.getPanCiphertext(), card.getId());
        // Logged as an audit fact; the details themselves never reach a log
        log.info("Card details revealed: cardId={}", card.getId());
        return cardMapper.toDetails(card, pan, crypto.cvv(pan, card.expiry()));
    }

    @Transactional
    public Card freeze(UUID userId) {
        Card card = currentCard(userId);
        if (card.getStatus() == CardStatus.ACTIVE) {
            card.freeze();
            log.info("Card frozen: cardId={}", card.getId());
        }
        return card;
    }

    @Transactional
    public Card unfreeze(UUID userId) {
        Card card = currentCard(userId);
        if (card.getStatus() == CardStatus.FROZEN) {
            card.unfreeze();
            log.info("Card unfrozen: cardId={}", card.getId());
        }
        return card;
    }

    @Transactional
    public Card cancel(UUID userId) {
        Card card = currentCard(userId);
        card.cancel();
        log.info("Card cancelled: cardId={}", card.getId());
        return card;
    }

    @Transactional
    public Card updateControls(UUID userId, UpdateControlsRequest request) {
        Card card = currentCard(userId);
        card.updateControls(request.perTransactionLimitMinor(), request.blockedCategories());
        log.info("Card controls updated: cardId={}", card.getId());
        return card;
    }

    @Override
    @Transactional(readOnly = true)
    public CardCheck checkForPayment(String pan, int expiryMonth, int expiryYear, Money amount, String mcc) {
        MerchantCategory category = MerchantCategories.fromMcc(mcc);
        Optional<Card> found = Luhn.isValid(pan) ? cards.findByPanHash(crypto.panHash(pan)) : Optional.empty();
        if (found.isEmpty()) {
            return CardCheck.decline(null, null, category, CardDeclineReason.CARD_NOT_FOUND);
        }

        Card card = found.get();
        CardDeclineReason reason = declineReason(card, expiryMonth, expiryYear, amount, category);
        if (reason != null) {
            return CardCheck.decline(card.getId(), card.getAccountId(), category, reason);
        }
        return CardCheck.approve(card.getId(), card.getAccountId(), category);
    }

    private CardDeclineReason declineReason(Card card, int expiryMonth, int expiryYear, Money amount,
                                            MerchantCategory category) {
        // Wrong expiry is treated like an unknown card, so it reveals nothing about the real card
        if (card.getExpiryMonth() != expiryMonth || card.getExpiryYear() != expiryYear) {
            return CardDeclineReason.CARD_NOT_FOUND;
        }
        if (card.getStatus() != CardStatus.ACTIVE) {
            return CardDeclineReason.CARD_NOT_ACTIVE;
        }
        if (card.isExpiredAt(currentMonth())) {
            return CardDeclineReason.CARD_EXPIRED;
        }
        if (!Money.EUR.equals(amount.currency())) {
            return CardDeclineReason.CURRENCY_NOT_SUPPORTED;
        }
        if (amount.amountMinor() > card.getPerTransactionLimitMinor()) {
            return CardDeclineReason.TRANSACTION_LIMIT_EXCEEDED;
        }
        if (card.getBlockedCategories().contains(category)) {
            return CardDeclineReason.MERCHANT_CATEGORY_BLOCKED;
        }
        return null;
    }

    private String newPan() {
        for (int attempt = 0; attempt < MAX_PAN_ATTEMPTS; attempt++) {
            StringBuilder digits = new StringBuilder(properties.bin());
            while (digits.length() < PAN_LENGTH - 1) {
                digits.append(random.nextInt(10));
            }
            String pan = digits.append(Luhn.checkDigit(digits.toString())).toString();
            if (!cards.existsByPanHash(crypto.panHash(pan))) {
                return pan;
            }
        }
        throw new IllegalStateException("Could not generate a unique card number");
    }

    private static YearMonth currentMonth() {
        return YearMonth.now(ZoneOffset.UTC);
    }
}