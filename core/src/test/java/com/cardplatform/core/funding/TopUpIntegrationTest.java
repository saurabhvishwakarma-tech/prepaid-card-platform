package com.cardplatform.core.funding;

import com.cardplatform.core.funding.service.FundingService;
import com.cardplatform.core.support.FakePaymentProvider;
import com.cardplatform.core.support.IntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TopUpIntegrationTest extends IntegrationTestSupport {

    private static final String VISA = "pm_card_visa";

    @Autowired
    private FakePaymentProvider provider;

    @Autowired
    private FundingService funding;

    @Test
    void signedWebhookCreditsTheTopUpExactlyOnce() throws Exception {
        String token = verifiedCustomerToken();
        UUID topUpId = startTopUp(token, newKey(), 5_000, VISA);
        String paymentId = provider.paymentIdFor(topUpId);

        String event = succeededEvent("evt_" + UUID.randomUUID(), paymentId, topUpId, 5_000);
        mockMvc.perform(webhook(event, sign(event))).andExpect(status().isOk());
        // Stripe retries deliveries, and can send a second event about the same payment
        mockMvc.perform(webhook(event, sign(event))).andExpect(status().isOk());
        String another = succeededEvent("evt_" + UUID.randomUUID(), paymentId, topUpId, 5_000);
        mockMvc.perform(webhook(another, sign(another))).andExpect(status().isOk());

        assertThat(availableBalance(token)).isEqualTo(5_000);
        mockMvc.perform(get("/api/core/topups/" + topUpId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void retryWithTheSameIdempotencyKeyGetsTheSameTopUpAndChargesOnce() throws Exception {
        String token = verifiedCustomerToken();
        String key = newKey();

        UUID first = startTopUp(token, key, 2_000, VISA);
        UUID retry = startTopUp(token, key, 2_000, VISA);

        assertThat(retry).isEqualTo(first);
        assertThat(provider.chargesFor(first)).isEqualTo(1);
    }

    @Test
    void anIdempotencyKeyCannotBeReusedForADifferentAmount() throws Exception {
        String token = verifiedCustomerToken();
        String key = newKey();
        startTopUp(token, key, 2_000, VISA);

        mockMvc.perform(topUp(token, key, 9_000, VISA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void webhookWithoutAValidSignatureIsRejectedAndChangesNothing() throws Exception {
        String token = verifiedCustomerToken();
        UUID topUpId = startTopUp(token, newKey(), 5_000, VISA);
        String event = succeededEvent("evt_" + UUID.randomUUID(), provider.paymentIdFor(topUpId), topUpId, 5_000);

        String forged = "t=" + Instant.now().getEpochSecond() + ",v1=" + "0".repeat(64);
        mockMvc.perform(webhook(event, forged)).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/core/webhooks/stripe").contentType(MediaType.APPLICATION_JSON).content(event))
                .andExpect(status().isBadRequest());

        assertThat(availableBalance(token)).isZero();
    }

    @Test
    void declinedCardLeavesTheTopUpFailed() throws Exception {
        String token = verifiedCustomerToken();

        mockMvc.perform(topUp(token, newKey(), 5_000, FakePaymentProvider.DECLINED_CARD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").value("generic_decline"));

        assertThat(availableBalance(token)).isZero();
    }

    @Test
    void reconciliationCompletesATopUpWhoseWebhookNeverArrived() throws Exception {
        String token = verifiedCustomerToken();
        UUID topUpId = startTopUp(token, newKey(), 3_000, VISA);

        funding.reconcilePendingBefore(Instant.now().plusSeconds(1));

        assertThat(availableBalance(token)).isEqualTo(3_000);
        mockMvc.perform(get("/api/core/topups/" + topUpId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void onlyVerifiedCustomersCanTopUp() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(register(email, PASSWORD)).andExpect(status().isCreated());
        String token = loginAndGetToken(email);

        mockMvc.perform(topUp(token, newKey(), 1_000, VISA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("KYC_REQUIRED"));
    }

    private UUID startTopUp(String token, String key, long amountMinor, String paymentMethodId) throws Exception {
        String body = mockMvc.perform(topUp(token, key, amountMinor, paymentMethodId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private long availableBalance(String token) throws Exception {
        String body = mockMvc.perform(get("/api/core/accounts/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.availableBalanceMinor")).longValue();
    }

    private static MockHttpServletRequestBuilder topUp(String token, String key, long amountMinor,
                                                       String paymentMethodId) {
        return post("/api/core/topups")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amountMinor": %d, "paymentMethodId": "%s"}
                        """.formatted(amountMinor, paymentMethodId));
    }

    private static MockHttpServletRequestBuilder webhook(String payload, String signature) {
        return post("/api/core/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", signature)
                .content(payload);
    }

    // Stripe's scheme: HMAC-SHA256 over "timestamp.payload" with the endpoint's signing secret
    private static String sign(String payload) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        return "t=" + timestamp + ",v1="
                + Webhook.Util.computeHmacSha256(STRIPE_WEBHOOK_SECRET, timestamp + "." + payload);
    }

    private static String succeededEvent(String eventId, String paymentId, UUID topUpId, long amountMinor) {
        return """
                {"id": "%s", "object": "event", "type": "payment_intent.succeeded", "created": %d,
                 "data": {"object": {"id": "%s", "object": "payment_intent", "amount": %d, "currency": "eur",
                                     "status": "succeeded", "metadata": {"top_up_id": "%s"}}}}
                """.formatted(eventId, Instant.now().getEpochSecond(), paymentId, amountMinor, topUpId);
    }

    private static String newKey() {
        return "key-" + UUID.randomUUID();
    }
}