package com.cardplatform.core.shared;

import com.cardplatform.contracts.events.v1.LedgerPostingEvent;
import com.cardplatform.contracts.events.v1.PostingDirection;
import com.cardplatform.core.ledger.api.LedgerApi;
import com.cardplatform.core.shared.money.Money;
import com.cardplatform.core.shared.outbox.OutboxWriter;
import com.cardplatform.core.shared.outbox.Topics;
import com.cardplatform.core.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class OutboxIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private LedgerApi ledger;

    @Autowired
    private OutboxWriter outboxWriter;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void topUpReachesKafkaAndIsMarkedPublished() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = ledger.openCustomerAccount(userId).accountId();
        String reference = "topup-" + UUID.randomUUID();

        ledger.creditTopUp(accountId, reference, Money.eur(4_200));

        LedgerPostingEvent event = awaitEvent(Topics.LEDGER_POSTINGS, LedgerPostingEvent.parser(),
                e -> e.getReference().equals(reference));
        assertThat(event.getAccountId()).isEqualTo(accountId.toString());
        assertThat(event.getUserId()).isEqualTo(userId.toString());
        assertThat(event.getDirection()).isEqualTo(PostingDirection.POSTING_DIRECTION_CREDIT);
        assertThat(event.getAmount().getAmountMinor()).isEqualTo(4_200);
        assertThat(event.getAvailableBalanceAfter().getAmountMinor()).isEqualTo(4_200);

        UUID eventId = UUID.fromString(event.getMetadata().getEventId());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "select published_at is not null from messaging.outbox where id = ?", Boolean.class, eventId))
                .isTrue());
    }

    @Test
    void eventsCanOnlyBeWrittenInsideABusinessTransaction() {
        assertThatThrownBy(() -> outboxWriter.write(Topics.LEDGER_POSTINGS, "key", "Test",
                OutboxWriter.newMetadata(), LedgerPostingEvent.getDefaultInstance()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}