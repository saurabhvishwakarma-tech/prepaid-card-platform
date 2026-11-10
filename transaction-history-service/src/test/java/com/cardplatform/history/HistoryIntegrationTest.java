package com.cardplatform.history;

import com.cardplatform.contracts.common.v1.DeclineReason;
import com.cardplatform.contracts.common.v1.EventMetadata;
import com.cardplatform.contracts.common.v1.MerchantCategory;
import com.cardplatform.contracts.common.v1.Merchant;
import com.cardplatform.contracts.common.v1.Money;
import com.cardplatform.contracts.events.v1.EntryType;
import com.cardplatform.contracts.events.v1.LedgerPostingEvent;
import com.cardplatform.contracts.events.v1.PaymentAuthorized;
import com.cardplatform.contracts.events.v1.PaymentCleared;
import com.cardplatform.contracts.events.v1.PaymentDeclined;
import com.cardplatform.contracts.events.v1.PaymentEvent;
import com.cardplatform.contracts.events.v1.PaymentRefunded;
import com.cardplatform.contracts.events.v1.PostingDirection;
import com.google.protobuf.Timestamp;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class HistoryIntegrationTest {

    private static final String PAYMENTS = "payments.events";
    private static final String LEDGER = "ledger.postings";

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");
    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.4.0");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    // In real use core creates these; here the test stands in for core
    @TestConfiguration
    static class SourceTopics {

        @Bean
        NewTopic paymentEvents() {
            return TopicBuilder.name(PAYMENTS).partitions(3).replicas(1).build();
        }

        @Bean
        NewTopic ledgerPostings() {
            return TopicBuilder.name(LEDGER).partitions(3).replicas(1).build();
        }
    }

    @Autowired
    private KafkaTemplate<String, byte[]> kafka;

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void paymentLifecycleIsShownAndRedeliveredEventsAreIgnored() throws Exception {
        UUID user = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        String txnId = "ntx-" + UUID.randomUUID();

        send(PAYMENTS, payment(user, account, txnId, MerchantCategory.MERCHANT_CATEGORY_GROCERIES)
                .setAuthorized(PaymentAuthorized.newBuilder().setAmount(eur(3_000)).setAuthCode("ABC123")).build());
        send(PAYMENTS, payment(user, account, txnId, MerchantCategory.MERCHANT_CATEGORY_GROCERIES)
                .setCleared(PaymentCleared.newBuilder().setClearingId("clr-" + UUID.randomUUID())
                        .setClearedAmount(eur(2_500)).setReleasedAmount(eur(500))).build());
        PaymentEvent refund = payment(user, account, txnId, MerchantCategory.MERCHANT_CATEGORY_GROCERIES)
                .setRefunded(PaymentRefunded.newBuilder().setRefundId("ref-" + UUID.randomUUID()).setAmount(eur(400)))
                .build();
        send(PAYMENTS, refund);
        // At-least-once delivery: the same event can arrive twice
        send(PAYMENTS, refund);
        // Same account, so same partition: once this one is visible, the duplicate before it has been handled
        String marker = "ntx-" + UUID.randomUUID();
        send(PAYMENTS, payment(user, account, marker, MerchantCategory.MERCHANT_CATEGORY_RETAIL)
                .setAuthorized(PaymentAuthorized.newBuilder().setAmount(eur(100)).setAuthCode("XYZ789")).build());

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                mockMvc.perform(history(user)).andExpect(jsonPath("$.totalItems").value(3)));

        mockMvc.perform(history(user).param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2));
        mockMvc.perform(history(user).param("category", "GROCERIES").param("status", "COMPLETED"))
                .andExpect(jsonPath("$.items[?(@.type == 'CARD_PAYMENT')].amountMinor").value(2_500))
                .andExpect(jsonPath("$.items[?(@.type == 'REFUND')].direction").value("CREDIT"));
    }

    @Test
    void customersOnlySeeTheirOwnTransactions() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID aliceAccount = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        send(LEDGER, topUp(alice, aliceAccount, 10_000));
        send(PAYMENTS, payment(alice, aliceAccount, "ntx-" + UUID.randomUUID(), MerchantCategory.MERCHANT_CATEGORY_TRAVEL)
                .setDeclined(PaymentDeclined.newBuilder().setAmount(eur(50_000))
                        .setReason(DeclineReason.DECLINE_REASON_INSUFFICIENT_FUNDS)).build());
        send(PAYMENTS, payment(bob, UUID.randomUUID(), "ntx-" + UUID.randomUUID(), MerchantCategory.MERCHANT_CATEGORY_OTHER)
                .setAuthorized(PaymentAuthorized.newBuilder().setAmount(eur(700)).setAuthCode("BOB123")).build());

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                mockMvc.perform(history(alice)).andExpect(jsonPath("$.totalItems").value(2)));

        mockMvc.perform(history(alice).param("status", "DECLINED"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].declineReason").value("INSUFFICIENT_FUNDS"));
        mockMvc.perform(history(bob))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].amountMinor").value(700));
    }

    @Test
    void monthlySpendingCountsCompletedPaymentsMinusRefunds() throws Exception {
        UUID user = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        String groceries = "ntx-" + UUID.randomUUID();

        send(LEDGER, topUp(user, account, 20_000));
        send(PAYMENTS, cleared(user, account, groceries, MerchantCategory.MERCHANT_CATEGORY_GROCERIES, 2_000));
        send(PAYMENTS, cleared(user, account, "ntx-" + UUID.randomUUID(), MerchantCategory.MERCHANT_CATEGORY_RESTAURANTS, 1_500));
        send(PAYMENTS, payment(user, account, groceries, MerchantCategory.MERCHANT_CATEGORY_GROCERIES)
                .setRefunded(PaymentRefunded.newBuilder().setRefundId("ref-" + UUID.randomUUID()).setAmount(eur(500)))
                .build());

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                mockMvc.perform(history(user)).andExpect(jsonPath("$.totalItems").value(4)));

        mockMvc.perform(get("/api/history/spending")
                        .param("month", YearMonth.now(ZoneOffset.UTC).toString())
                        .with(jwt().jwt(token -> token.subject(user.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMinor").value(3_000))
                .andExpect(jsonPath("$.categories[?(@.category == 'GROCERIES')].amountMinor").value(1_500))
                .andExpect(jsonPath("$.categories[?(@.category == 'RESTAURANTS')].amountMinor").value(1_500));
    }

    @Test
    void unreadableMessageIsMovedToTheDeadLetterTopic() throws Exception {
        // 'n' is wire type 6, which doesn't exist in Protobuf, so this can never be parsed
        byte[] garbage = ("not protobuf " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        kafka.send(PAYMENTS, "bad-key", garbage).get(10, TimeUnit.SECONDS);

        Properties config = new Properties();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(PAYMENTS + ".DLT"));
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                for (ConsumerRecord<String, byte[]> record : consumer.poll(Duration.ofMillis(500))) {
                    if (Arrays.equals(record.value(), garbage)) {
                        return true;
                    }
                }
                return false;
            });
        }
    }

    private MockHttpServletRequestBuilder history(UUID user) {
        return get("/api/history/transactions").with(jwt().jwt(token -> token.subject(user.toString())));
    }

    private void send(String topic, PaymentEvent event) throws Exception {
        kafka.send(topic, event.getAccountId(), event.toByteArray()).get(10, TimeUnit.SECONDS);
    }

    private void send(String topic, LedgerPostingEvent event) throws Exception {
        kafka.send(topic, event.getAccountId(), event.toByteArray()).get(10, TimeUnit.SECONDS);
    }

    private static PaymentEvent.Builder payment(UUID user, UUID account, String txnId, MerchantCategory category) {
        return PaymentEvent.newBuilder()
                .setMetadata(metadata())
                .setUserId(user.toString())
                .setAccountId(account.toString())
                .setCardId(UUID.randomUUID().toString())
                .setNetworkTxnId(txnId)
                .setMerchant(Merchant.newBuilder().setMerchantId("m-1").setName("Corner Shop").setMcc("5411").setCountry("IE"))
                .setCategory(category);
    }

    private static PaymentEvent cleared(UUID user, UUID account, String txnId, MerchantCategory category, long amount) {
        return payment(user, account, txnId, category)
                .setCleared(PaymentCleared.newBuilder().setClearingId("clr-" + UUID.randomUUID())
                        .setClearedAmount(eur(amount)).setReleasedAmount(eur(0)))
                .build();
    }

    private static LedgerPostingEvent topUp(UUID user, UUID account, long amount) {
        return LedgerPostingEvent.newBuilder()
                .setMetadata(metadata())
                .setUserId(user.toString())
                .setAccountId(account.toString())
                .setJournalEntryId(UUID.randomUUID().toString())
                .setEntryType(EntryType.ENTRY_TYPE_TOP_UP)
                .setDirection(PostingDirection.POSTING_DIRECTION_CREDIT)
                .setAmount(eur(amount))
                .setLedgerBalanceAfter(eur(amount))
                .setAvailableBalanceAfter(eur(amount))
                .setReference("topup-" + UUID.randomUUID())
                .build();
    }

    private static EventMetadata metadata() {
        Instant now = Instant.now();
        return EventMetadata.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Timestamp.newBuilder().setSeconds(now.getEpochSecond()).setNanos(now.getNano()))
                .build();
    }

    private static Money eur(long amountMinor) {
        return Money.newBuilder().setAmountMinor(amountMinor).setCurrency("EUR").build();
    }
}