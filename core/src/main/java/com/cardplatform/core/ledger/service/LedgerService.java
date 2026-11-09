package com.cardplatform.core.ledger.service;

import com.cardplatform.core.ledger.api.AccountView;
import com.cardplatform.core.ledger.api.HoldResult;
import com.cardplatform.core.ledger.api.LedgerApi;
import com.cardplatform.core.ledger.exception.AccountNotFoundException;
import com.cardplatform.core.ledger.exception.HoldNotFoundException;
import com.cardplatform.core.ledger.exception.LedgerRuleException;
import com.cardplatform.core.ledger.mapper.AccountMapper;
import com.cardplatform.core.ledger.model.Account;
import com.cardplatform.core.ledger.model.EntryType;
import com.cardplatform.core.ledger.model.Hold;
import com.cardplatform.core.ledger.model.HoldStatus;
import com.cardplatform.core.ledger.model.JournalEntry;
import com.cardplatform.core.ledger.model.Posting;
import com.cardplatform.core.ledger.repository.AccountRepository;
import com.cardplatform.core.ledger.repository.HoldRepository;
import com.cardplatform.core.ledger.repository.JournalEntryRepository;
import com.cardplatform.core.ledger.repository.PostingRepository;
import com.cardplatform.core.shared.money.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class LedgerService implements LedgerApi {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    // System accounts seeded by V2: the other side of every customer posting
    private static final String TOP_UP_FUNDING = "TOP_UP_FUNDING";
    private static final String MERCHANT_SETTLEMENT = "MERCHANT_SETTLEMENT";

    private final AccountRepository accounts;
    private final HoldRepository holds;
    private final JournalEntryRepository journalEntries;
    private final PostingRepository postings;
    private final AccountMapper accountMapper;

    public LedgerService(AccountRepository accounts, HoldRepository holds,
                         JournalEntryRepository journalEntries, PostingRepository postings,
                         AccountMapper accountMapper) {
        this.accounts = accounts;
        this.holds = holds;
        this.journalEntries = journalEntries;
        this.postings = postings;
        this.accountMapper = accountMapper;
    }

    @Override
    @Transactional
    public AccountView openCustomerAccount(UUID userId) {
        Account account = accounts.findByUserIdAndCurrency(userId, Money.EUR)
                .orElseGet(() -> accounts.save(Account.openCustomer(userId, Money.EUR)));
        return accountMapper.toView(account);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountView> findCustomerAccount(UUID userId) {
        return accounts.findByUserIdAndCurrency(userId, Money.EUR).map(accountMapper::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountView getAccount(UUID accountId) {
        return accounts.findById(accountId).map(accountMapper::toView)
                .orElseThrow(AccountNotFoundException::new);
    }

    @Override
    @Transactional
    public HoldResult placeHold(UUID accountId, String reference, Money amount) {
        requirePositive(amount);
        Account account = lockAccounts(accountId).get(accountId);
        requireCustomerAccount(account, amount);

        // Checked after the lock, so a retry racing the first attempt sees its hold
        Optional<Hold> existing = holds.findByReference(reference);
        if (existing.isPresent()) {
            if (!existing.get().matches(accountId, amount)) {
                throw new LedgerRuleException("HOLD_REFERENCE_CONFLICT", "Reference already used for a different hold");
            }
            return HoldResult.PLACED;
        }
        if (account.getAvailableBalance() < amount.amountMinor()) {
            return HoldResult.INSUFFICIENT_FUNDS;
        }
        account.reserve(amount.amountMinor());
        holds.save(Hold.place(accountId, reference, amount));
        return HoldResult.PLACED;
    }

    @Override
    @Transactional
    public void captureHold(String reference, Money amount) {
        requirePositive(amount);
        Hold hold = holds.findByReferenceForUpdate(reference).orElseThrow(HoldNotFoundException::new);
        if (hold.getStatus() == HoldStatus.CAPTURED) {
            return; // a retried clearing: the money has already moved
        }
        requireActive(hold);
        requireSameCurrency(hold.getCurrency(), amount.currency());
        if (amount.amountMinor() > hold.getAmount()) {
            throw new LedgerRuleException("AMOUNT_EXCEEDS_HOLD", "Captured amount is larger than the hold");
        }

        UUID settlementId = systemAccountId(MERCHANT_SETTLEMENT);
        Map<UUID, Account> locked = lockAccounts(hold.getAccountId(), settlementId);
        Account customer = locked.get(hold.getAccountId());

        // The whole hold comes back to available, then only the cleared amount actually leaves
        customer.unreserve(hold.getAmount());
        hold.capture();
        postEntry(EntryType.CARD_CAPTURE, reference, amount.currency(),
                new Line(customer, -amount.amountMinor()),
                new Line(locked.get(settlementId), amount.amountMinor()));
        log.info("Hold captured: accountId={}, reference={}, amountMinor={}",
                customer.getId(), reference, amount.amountMinor());
    }

    @Override
    @Transactional
    public void releaseHold(String reference) {
        Hold hold = holds.findByReferenceForUpdate(reference).orElseThrow(HoldNotFoundException::new);
        if (hold.getStatus() == HoldStatus.RELEASED) {
            return; // a retried reversal changes nothing
        }
        requireActive(hold);
        Account account = lockAccounts(hold.getAccountId()).get(hold.getAccountId());
        account.unreserve(hold.getAmount());
        hold.release();
        log.info("Hold released: accountId={}, reference={}", account.getId(), reference);
    }

    @Override
    @Transactional
    public void creditTopUp(UUID accountId, String reference, Money amount) {
        credit(EntryType.TOP_UP, TOP_UP_FUNDING, accountId, reference, amount);
    }

    @Override
    @Transactional
    public void creditRefund(UUID accountId, String reference, Money amount) {
        credit(EntryType.CARD_REFUND, MERCHANT_SETTLEMENT, accountId, reference, amount);
    }

    private void credit(EntryType type, String sourceCode, UUID accountId, String reference, Money amount) {
        requirePositive(amount);
        UUID sourceId = systemAccountId(sourceCode);
        Map<UUID, Account> locked = lockAccounts(accountId, sourceId);

        // Checked after the lock, so a duplicate arriving at the same moment sees the first one's entry
        if (journalEntries.existsByEntryTypeAndReference(type, reference)) {
            return;
        }
        Account customer = locked.get(accountId);
        requireCustomerAccount(customer, amount);
        postEntry(type, reference, amount.currency(),
                new Line(locked.get(sourceId), -amount.amountMinor()),
                new Line(customer, amount.amountMinor()));
        log.info("{} posted: accountId={}, reference={}, amountMinor={}",
                type, accountId, reference, amount.amountMinor());
    }

    // Double-entry: postings must sum to zero, or money was created or destroyed
    private void postEntry(EntryType type, String reference, String currency, Line... lines) {
        long sum = Arrays.stream(lines).mapToLong(Line::amount).sum();
        if (lines.length < 2 || sum != 0) {
            throw new IllegalStateException("A journal entry needs at least two postings that sum to zero");
        }
        JournalEntry entry = journalEntries.save(JournalEntry.create(type, reference));
        for (Line line : lines) {
            requireSameCurrency(line.account().getCurrency(), currency);
            line.account().post(line.amount());
            postings.save(Posting.create(entry.getId(), line.account().getId(), line.amount(), currency));
        }
    }

    // Always lock in ID order: two transactions locking the same accounts can then never deadlock
    private Map<UUID, Account> lockAccounts(UUID... accountIds) {
        Map<UUID, Account> locked = new HashMap<>();
        Arrays.stream(accountIds).distinct().sorted().forEach(id ->
                locked.put(id, accounts.findByIdForUpdate(id).orElseThrow(AccountNotFoundException::new)));
        return locked;
    }

    private UUID systemAccountId(String code) {
        return accounts.findIdByCode(code)
                .orElseThrow(() -> new IllegalStateException("Missing system account " + code));
    }

    private static void requirePositive(Money amount) {
        if (!amount.isPositive()) {
            throw new LedgerRuleException("AMOUNT_NOT_POSITIVE", "Amount must be greater than zero");
        }
    }

    private static void requireCustomerAccount(Account account, Money amount) {
        if (!account.isCustomer()) {
            throw new LedgerRuleException("NOT_A_CUSTOMER_ACCOUNT", "Operation needs a customer account");
        }
        requireSameCurrency(account.getCurrency(), amount.currency());
    }

    private static void requireActive(Hold hold) {
        if (hold.getStatus() != HoldStatus.ACTIVE) {
            throw new LedgerRuleException("HOLD_NOT_ACTIVE", "Hold is " + hold.getStatus());
        }
    }

    private static void requireSameCurrency(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new LedgerRuleException("CURRENCY_MISMATCH", "Expected " + expected + " but got " + actual);
        }
    }

    private record Line(Account account, long amount) {
    }
}