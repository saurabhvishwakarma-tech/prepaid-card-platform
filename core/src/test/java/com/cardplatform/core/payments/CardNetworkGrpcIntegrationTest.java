package com.cardplatform.core.payments;


import com.cardplatform.contracts.events.v1.PaymentEvent;
import com.cardplatform.core.shared.outbox.Topics;
import com.cardplatform.contracts.common.v1.DeclineReason;
import com.cardplatform.contracts.common.v1.Merchant;
import com.cardplatform.contracts.network.v1.AuthorizationDecision;
import com.cardplatform.contracts.network.v1.AuthorizeRequest;
import com.cardplatform.contracts.network.v1.AuthorizeResponse;
import com.cardplatform.contracts.network.v1.CardNetworkServiceGrpc;
import com.cardplatform.contracts.network.v1.CardNetworkServiceGrpc.CardNetworkServiceBlockingStub;
import com.cardplatform.contracts.network.v1.ClearRequest;
import com.cardplatform.contracts.network.v1.ClearResponse;
import com.cardplatform.contracts.network.v1.ProcessingResult;
import com.cardplatform.contracts.network.v1.RefundRequest;
import com.cardplatform.contracts.network.v1.RefundResponse;
import com.cardplatform.contracts.network.v1.RejectReason;
import com.cardplatform.contracts.network.v1.ReverseRequest;
import com.cardplatform.contracts.network.v1.ReverseResponse;
import com.cardplatform.core.ledger.api.AccountView;
import com.cardplatform.core.ledger.api.LedgerApi;
import com.cardplatform.core.shared.money.Money;
import com.cardplatform.core.support.IntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.grpc.client.GrpcChannelFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.cardplatform.core.payments.service.PaymentService;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CardNetworkGrpcIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private GrpcChannelFactory channels;

    @Autowired
    private LedgerApi ledger;

    @Autowired
    private PaymentService payments;

    private ManagedChannel channel;

    @BeforeEach
    void openChannel() {
        channel = channels.createChannel("default");
    }

    @AfterEach
    void closeChannel() {
        channel.shutdownNow();
    }

    @Test
    void approvalPlacesAHoldAndARetryGetsTheSameAnswer() throws Exception {
        Cardholder cardholder = fundedCardholder(10_000);
        AuthorizeRequest request = authorization(cardholder, txnId(), 2_500);

        AuthorizeResponse first = network().authorize(request);
        AuthorizeResponse retry = network().authorize(request);

        assertThat(first.getDecision()).isEqualTo(AuthorizationDecision.AUTHORIZATION_DECISION_APPROVED);
        assertThat(first.getAuthCode()).hasSize(6);
        assertThat(retry).isEqualTo(first);
        assertBalances(cardholder, 10_000, 7_500);
    }

    @Test
    void concurrentCopiesOfOneAuthorisationHoldTheMoneyOnce() throws Exception {
        Cardholder cardholder = fundedCardholder(10_000);
        AuthorizeRequest request = authorization(cardholder, txnId(), 4_000);
        CardNetworkServiceBlockingStub network = network();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<AuthorizeResponse>> responses = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            for (int i = 0; i < 10; i++) {
                responses.add(pool.submit(() -> {
                    start.await();
                    return network.authorize(request);
                }));
            }
            start.countDown();

            Set<String> authCodes = new HashSet<>();
            for (Future<AuthorizeResponse> response : responses) {
                authCodes.add(response.get(30, TimeUnit.SECONDS).getAuthCode());
            }
            // Every copy got the same approval, and the money was held only once
            assertThat(authCodes).hasSize(1);
        }
        assertBalances(cardholder, 10_000, 6_000);
    }

    @Test
    void insufficientFundsIsDeclinedWithoutTouchingTheBalance() throws Exception {
        Cardholder cardholder = fundedCardholder(1_000);

        AuthorizeResponse response = network().authorize(authorization(cardholder, txnId(), 1_500));

        assertThat(response.getDecision()).isEqualTo(AuthorizationDecision.AUTHORIZATION_DECISION_DECLINED);
        assertThat(response.getDeclineReason()).isEqualTo(DeclineReason.DECLINE_REASON_INSUFFICIENT_FUNDS);
        assertBalances(cardholder, 1_000, 1_000);
    }

    @Test
    void unknownCardIsDeclined() throws Exception {
        Cardholder stranger = new Cardholder(null, "4111111111111111", 12, 2030);

        AuthorizeResponse response = network().authorize(authorization(stranger, txnId(), 100));

        assertThat(response.getDecision()).isEqualTo(AuthorizationDecision.AUTHORIZATION_DECISION_DECLINED);
        assertThat(response.getDeclineReason()).isEqualTo(DeclineReason.DECLINE_REASON_CARD_NOT_FOUND);
    }

    @Test
    void clearingForLessReleasesTheRestAndBlocksALaterReversal() throws Exception {
        Cardholder cardholder = fundedCardholder(10_000);
        String txnId = txnId();
        network().authorize(authorization(cardholder, txnId, 3_000));

        String clearingId = "clr-" + UUID.randomUUID();
        ClearResponse cleared = network().clear(clear(clearingId, txnId, 2_500));
        ClearResponse retried = network().clear(clear(clearingId, txnId, 2_500));

        assertThat(cleared.getResult()).isEqualTo(ProcessingResult.PROCESSING_RESULT_ACCEPTED);
        assertThat(retried).isEqualTo(cleared);
        assertBalances(cardholder, 7_500, 7_500);

        ReverseResponse lateReversal = network().reverse(reverse(txnId));
        assertThat(lateReversal.getResult()).isEqualTo(ProcessingResult.PROCESSING_RESULT_REJECTED);
        assertThat(lateReversal.getRejectReason()).isEqualTo(RejectReason.REJECT_REASON_INVALID_STATE);
    }

    @Test
    void reversalReleasesTheHoldAndBlocksALaterClearing() throws Exception {
        Cardholder cardholder = fundedCardholder(5_000);
        String txnId = txnId();
        network().authorize(authorization(cardholder, txnId, 2_000));

        ReverseResponse reversed = network().reverse(reverse(txnId));

        assertThat(reversed.getResult()).isEqualTo(ProcessingResult.PROCESSING_RESULT_ACCEPTED);
        assertBalances(cardholder, 5_000, 5_000);

        ClearResponse lateClearing = network().clear(clear("clr-" + UUID.randomUUID(), txnId, 2_000));
        assertThat(lateClearing.getRejectReason()).isEqualTo(RejectReason.REJECT_REASON_INVALID_STATE);
    }

    @Test
    void refundsCannotExceedTheClearedAmount() throws Exception {
        Cardholder cardholder = fundedCardholder(5_000);
        String txnId = txnId();
        network().authorize(authorization(cardholder, txnId, 2_000));
        network().clear(clear("clr-" + UUID.randomUUID(), txnId, 2_000));

        RefundResponse partial = network().refund(refund(txnId, 500));
        RefundResponse tooMuch = network().refund(refund(txnId, 1_600));

        assertThat(partial.getResult()).isEqualTo(ProcessingResult.PROCESSING_RESULT_ACCEPTED);
        assertThat(tooMuch.getRejectReason()).isEqualTo(RejectReason.REJECT_REASON_AMOUNT_EXCEEDS_ALLOWED);
        assertBalances(cardholder, 3_500, 3_500);
    }

    @Test
    void authorisationAndClearingArePublishedForOtherServices() throws Exception {
        Cardholder cardholder = fundedCardholder(5_000);
        String txnId = txnId();
        network().authorize(authorization(cardholder, txnId, 2_000));
        network().clear(clear("clr-" + UUID.randomUUID(), txnId, 1_500));

        PaymentEvent authorized = awaitEvent(Topics.PAYMENT_EVENTS, PaymentEvent.parser(),
                e -> e.getNetworkTxnId().equals(txnId) && e.hasAuthorized());
        PaymentEvent cleared = awaitEvent(Topics.PAYMENT_EVENTS, PaymentEvent.parser(),
                e -> e.getNetworkTxnId().equals(txnId) && e.hasCleared());

        assertThat(authorized.getAccountId()).isEqualTo(cardholder.accountId().toString());
        assertThat(authorized.getUserId()).isNotBlank();
        assertThat(authorized.getAuthorized().getAmount().getAmountMinor()).isEqualTo(2_000);
        assertThat(cleared.getCleared().getClearedAmount().getAmountMinor()).isEqualTo(1_500);
        assertThat(cleared.getCleared().getReleasedAmount().getAmountMinor()).isEqualTo(500);
    }

    @Test
    void staleAuthorisationExpiresAndReleasesTheHold() throws Exception {
        Cardholder cardholder = fundedCardholder(5_000);
        String txnId = txnId();
        network().authorize(authorization(cardholder, txnId, 2_000));
        assertBalances(cardholder, 5_000, 3_000);

        int expired = payments.expireAuthorizationsBefore(Instant.now().plusSeconds(1));

        assertThat(expired).isPositive();
        assertBalances(cardholder, 5_000, 5_000);
        ClearResponse lateClearing = network().clear(clear("clr-" + UUID.randomUUID(), txnId, 2_000));
        assertThat(lateClearing.getRejectReason()).isEqualTo(RejectReason.REJECT_REASON_INVALID_STATE);
    }


    @Test
    void clearingAnUnknownAuthorisationIsRejected() {
        ClearResponse response = network().clear(clear("clr-" + UUID.randomUUID(), txnId(), 100));

        assertThat(response.getResult()).isEqualTo(ProcessingResult.PROCESSING_RESULT_REJECTED);
        assertThat(response.getRejectReason()).isEqualTo(RejectReason.REJECT_REASON_AUTHORIZATION_NOT_FOUND);
    }

    @Test
    void malformedRequestIsRejectedAsInvalidArgument() {
        Cardholder stranger = new Cardholder(null, "4111111111111111", 12, 2030);

        assertThatThrownBy(() -> network().authorize(authorization(stranger, txnId(), 0)))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
    }

    @Test
    void callsWithoutValidNetworkCredentialsAreRefused() {
        AuthorizeRequest request = authorization(new Cardholder(null, "4111111111111111", 12, 2030), txnId(), 100);

        assertRefused(() -> stub(NETWORK_CLIENT_ID, "wrong-secret").authorize(request));
        assertRefused(() -> CardNetworkServiceGrpc.newBlockingStub(channel).authorize(request));
    }

    private CardNetworkServiceBlockingStub network() {
        return stub(NETWORK_CLIENT_ID, NETWORK_CLIENT_SECRET);
    }

    private CardNetworkServiceBlockingStub stub(String clientId, String secret) {
        Metadata headers = new Metadata();
        String credentials = Base64.getEncoder()
                .encodeToString((clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Basic " + credentials);
        return CardNetworkServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    private Cardholder fundedCardholder(long amountMinor) throws Exception {
        String token = verifiedCustomerToken();
        mockMvc.perform(post("/api/core/cards").header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
        String card = mockMvc.perform(post("/api/core/cards/me/reveal").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String account = mockMvc.perform(get("/api/core/accounts/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        UUID accountId = UUID.fromString(JsonPath.read(account, "$.accountId"));
        ledger.creditTopUp(accountId, "topup-" + UUID.randomUUID(), Money.eur(amountMinor));
        return new Cardholder(accountId, JsonPath.read(card, "$.pan"),
                JsonPath.read(card, "$.expiryMonth"), JsonPath.read(card, "$.expiryYear"));
    }

    private void assertBalances(Cardholder cardholder, long ledgerBalance, long availableBalance) {
        AccountView account = ledger.getAccount(cardholder.accountId());
        assertThat(account.ledgerBalance()).isEqualTo(ledgerBalance);
        assertThat(account.availableBalance()).isEqualTo(availableBalance);
    }

    private static void assertRefused(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(StatusRuntimeException.class,
                e -> assertThat(e.getStatus().getCode())
                        .isIn(Status.Code.UNAUTHENTICATED, Status.Code.PERMISSION_DENIED));
    }

    private static AuthorizeRequest authorization(Cardholder cardholder, String networkTxnId, long amountMinor) {
        return AuthorizeRequest.newBuilder()
                .setNetworkTxnId(networkTxnId)
                .setPan(cardholder.pan())
                .setExpiryMonth(cardholder.expiryMonth())
                .setExpiryYear(cardholder.expiryYear())
                .setAmount(eur(amountMinor))
                .setMerchant(Merchant.newBuilder()
                        .setMerchantId("m-1")
                        .setName("Corner Shop")
                        .setMcc("5411")
                        .setCountry("IE"))
                .build();
    }

    private static ClearRequest clear(String clearingId, String networkTxnId, long amountMinor) {
        return ClearRequest.newBuilder()
                .setClearingId(clearingId)
                .setNetworkTxnId(networkTxnId)
                .setAmount(eur(amountMinor))
                .build();
    }

    private static ReverseRequest reverse(String networkTxnId) {
        return ReverseRequest.newBuilder()
                .setReversalId("rev-" + UUID.randomUUID())
                .setNetworkTxnId(networkTxnId)
                .build();
    }

    private static RefundRequest refund(String networkTxnId, long amountMinor) {
        return RefundRequest.newBuilder()
                .setRefundId("ref-" + UUID.randomUUID())
                .setNetworkTxnId(networkTxnId)
                .setAmount(eur(amountMinor))
                .build();
    }

    private static com.cardplatform.contracts.common.v1.Money eur(long amountMinor) {
        return com.cardplatform.contracts.common.v1.Money.newBuilder()
                .setAmountMinor(amountMinor)
                .setCurrency("EUR")
                .build();
    }

    private static String txnId() {
        return "ntx-" + UUID.randomUUID();
    }

    private record Cardholder(UUID accountId, String pan, int expiryMonth, int expiryYear) {
    }
}