package com.cardplatform.core.ledger;

import com.cardplatform.core.ledger.api.AccountView;
import com.cardplatform.core.ledger.api.HoldResult;
import com.cardplatform.core.ledger.api.LedgerApi;
import com.cardplatform.core.ledger.exception.LedgerRuleException;
import com.cardplatform.core.ledger.model.EntryType;
import com.cardplatform.core.ledger.model.Posting;
import com.cardplatform.core.ledger.repository.JournalEntryRepository;
import com.cardplatform.core.ledger.repository.PostingRepository;
import com.cardplatform.core.shared.money.Money;
import com.cardplatform.core.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LedgerIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private LedgerApi ledger;

    @Autowired
    private JournalEntryRepository journalEntries;

    @Autowired
    private PostingRepository postings;

    @Test
    void registrationOpensAnEmptyEuroAccount() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(register(email, PASSWORD)).andExpect(status().isCreated());
        String token = loginAndGetToken(email);

        mockMvc.perform(get("/api/core/accounts/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.ledgerBalanceMinor").value(0))
                .andExpect(jsonPath("$.availableBalanceMinor").value(0));
    }

    @Test
    void topUpIsBalancedAndAppliedOnlyOnce() {
        UUID accountId = newAccount();
        String reference = reference();

        ledger.creditTopUp(accountId, reference, Money.eur(10_000));
        ledger.creditTopUp(accountId, reference, Money.eur(10_000));

        assertBalances(accountId, 10_000, 10_000);
        UUID entryId = journalEntries.findByEntryTypeAndReference(EntryType.TOP_UP, reference).orElseThrow().getId();
        List<Posting> lines = postings.findByJournalEntryId(entryId);
        assertThat(lines).hasSize(2);
        assertThat(lines.stream().mapToLong(Posting::getAmount).sum()).isZero();
    }

    @Test
    void captureForLessThanTheHoldGivesTheRestBack() {
        UUID accountId = fundedAccount(10_000);
        String reference = reference();

        assertThat(ledger.placeHold(accountId, reference, Money.eur(3_000))).isEqualTo(HoldResult.PLACED);
        assertBalances(accountId, 10_000, 7_000);

        assertThatThrownBy(() -> ledger.captureHold(reference, Money.eur(3_001)))
                .isInstanceOf(LedgerRuleException.class);

        ledger.captureHold(reference, Money.eur(2_500));
        ledger.captureHold(reference, Money.eur(2_500));
        assertBalances(accountId, 7_500, 7_500);
    }

    @Test
    void releasedHoldRestoresTheAvailableBalance() {
        UUID accountId = fundedAccount(5_000);
        String reference = reference();

        ledger.placeHold(accountId, reference, Money.eur(2_000));
        ledger.releaseHold(reference);
        ledger.releaseHold(reference);

        assertBalances(accountId, 5_000, 5_000);
    }

    @Test
    void holdLargerThanTheAvailableBalanceIsDeclined() {
        UUID accountId = fundedAccount(1_000);

        assertThat(ledger.placeHold(accountId, reference(), Money.eur(1_001)))
                .isEqualTo(HoldResult.INSUFFICIENT_FUNDS);
        assertBalances(accountId, 1_000, 1_000);
    }

    @Test
    void refundAfterCaptureCreditsTheCustomer() {
        UUID accountId = fundedAccount(5_000);
        String reference = reference();

        ledger.placeHold(accountId, reference, Money.eur(2_000));
        ledger.captureHold(reference, Money.eur(2_000));
        ledger.creditRefund(accountId, "refund-" + reference, Money.eur(500));

        assertBalances(accountId, 3_500, 3_500);
    }

    @Test
    void parallelHoldsNeverOverspend() throws Exception {
        UUID accountId = fundedAccount(10_000);
        int attempts = 50;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<HoldResult>> results = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(attempts)) {
            for (int i = 0; i < attempts; i++) {
                String reference = reference();
                results.add(pool.submit(() -> {
                    start.await();
                    return ledger.placeHold(accountId, reference, Money.eur(300));
                }));
            }
            // Release all 50 threads at once so they really compete for the same account
            start.countDown();

            long placed = 0;
            for (Future<HoldResult> result : results) {
                if (result.get(30, TimeUnit.SECONDS) == HoldResult.PLACED) {
                    placed++;
                }
            }

            // 10,000 / 300 = 33 holds fit; a 34th would take the available balance below zero
            assertThat(placed).isEqualTo(33);
        }
        assertBalances(accountId, 10_000, 10_000 - 33 * 300);
    }

    private UUID newAccount() {
        return ledger.openCustomerAccount(UUID.randomUUID()).accountId();
    }

    private UUID fundedAccount(long amountMinor) {
        UUID accountId = newAccount();
        ledger.creditTopUp(accountId, reference(), Money.eur(amountMinor));
        return accountId;
    }

    private void assertBalances(UUID accountId, long ledgerBalance, long availableBalance) {
        AccountView account = ledger.getAccount(accountId);
        assertThat(account.ledgerBalance()).isEqualTo(ledgerBalance);
        assertThat(account.availableBalance()).isEqualTo(availableBalance);
    }

    private static String reference() {
        return "ref-" + UUID.randomUUID();
    }
}