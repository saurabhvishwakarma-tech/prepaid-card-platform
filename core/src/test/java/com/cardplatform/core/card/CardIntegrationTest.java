package com.cardplatform.core.card;

import com.cardplatform.core.card.api.CardApi;
import com.cardplatform.core.card.api.CardCheck;
import com.cardplatform.core.card.api.CardDeclineReason;
import com.cardplatform.core.card.api.MerchantCategory;
import com.cardplatform.core.card.service.Luhn;
import com.cardplatform.core.shared.money.Money;
import com.cardplatform.core.support.IntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CardIntegrationTest extends IntegrationTestSupport {

    private static final String GROCERY_MCC = "5411";
    private static final String GAMBLING_MCC = "7995";

    @Autowired
    private CardApi cardApi;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void cardNeedsVerifiedKyc() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(register(email, PASSWORD)).andExpect(status().isCreated());
        String token = loginAndGetToken(email);

        mockMvc.perform(post("/api/core/cards").header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("KYC_NOT_VERIFIED"));
    }

    @Test
    void issuedCardIsMaskedAndStoredEncrypted() throws Exception {
        String token = verifiedCustomerToken();

        String issued = mockMvc.perform(post("/api/core/cards").header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.maskedPan").value(startsWith("**** **** **** ")))
                .andExpect(jsonPath("$.pan").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        UUID cardId = UUID.fromString(JsonPath.read(issued, "$.id"));
        String maskedPan = JsonPath.read(issued, "$.maskedPan");

        RevealedCard card = reveal(token);
        assertThat(card.pan()).hasSize(16).startsWith("990001").endsWith(maskedPan.substring(maskedPan.length() - 4));
        assertThat(Luhn.isValid(card.pan())).isTrue();
        assertThat(card.cvv()).matches("\\d{3}");
        assertThat(reveal(token).cvv()).isEqualTo(card.cvv());

        byte[] stored = jdbc.queryForObject("select pan_ciphertext from card.cards where id = ?", byte[].class, cardId);
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain(card.pan());
    }

    @Test
    void onlyOneCurrentCardPerUser() throws Exception {
        String token = verifiedCustomerToken();
        issue(token);

        mockMvc.perform(post("/api/core/cards").header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CARD_ALREADY_EXISTS"));

        mockMvc.perform(post("/api/core/cards/me/cancel").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post("/api/core/cards").header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    @Test
    void frozenCardIsDeclinedUntilUnfrozen() throws Exception {
        String token = verifiedCustomerToken();
        issue(token);
        RevealedCard card = reveal(token);
        assertThat(check(card, 1_000, GROCERY_MCC).approved()).isTrue();

        mockMvc.perform(post("/api/core/cards/me/freeze").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));
        assertThat(check(card, 1_000, GROCERY_MCC).declineReason()).isEqualTo(CardDeclineReason.CARD_NOT_ACTIVE);

        mockMvc.perform(post("/api/core/cards/me/unfreeze").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(check(card, 1_000, GROCERY_MCC).approved()).isTrue();
    }

    @Test
    void spendControlsDeclineOverLimitAndBlockedCategories() throws Exception {
        String token = verifiedCustomerToken();
        issue(token);
        RevealedCard card = reveal(token);

        mockMvc.perform(put("/api/core/cards/me/controls").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"perTransactionLimitMinor": 5000, "blockedCategories": ["GAMBLING"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perTransactionLimitMinor").value(5000))
                .andExpect(jsonPath("$.blockedCategories[0]").value("GAMBLING"));

        assertThat(check(card, 5_001, GROCERY_MCC).declineReason())
                .isEqualTo(CardDeclineReason.TRANSACTION_LIMIT_EXCEEDED);
        assertThat(check(card, 1_000, GAMBLING_MCC).declineReason())
                .isEqualTo(CardDeclineReason.MERCHANT_CATEGORY_BLOCKED);

        CardCheck approved = check(card, 1_000, GROCERY_MCC);
        assertThat(approved.approved()).isTrue();
        assertThat(approved.category()).isEqualTo(MerchantCategory.GROCERIES);
        assertThat(approved.accountId()).isNotNull();
    }

    @Test
    void unknownCardOrWrongExpiryIsDeclined() throws Exception {
        String token = verifiedCustomerToken();
        issue(token);
        RevealedCard card = reveal(token);

        assertThat(cardApi.checkForPayment("4111111111111111", card.expiryMonth(), card.expiryYear(),
                Money.eur(100), GROCERY_MCC).declineReason()).isEqualTo(CardDeclineReason.CARD_NOT_FOUND);
        assertThat(cardApi.checkForPayment(card.pan(), card.expiryMonth(), card.expiryYear() + 1,
                Money.eur(100), GROCERY_MCC).declineReason()).isEqualTo(CardDeclineReason.CARD_NOT_FOUND);
    }

    private void issue(String token) throws Exception {
        mockMvc.perform(post("/api/core/cards").header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    private RevealedCard reveal(String token) throws Exception {
        String body = mockMvc.perform(post("/api/core/cards/me/reveal").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new RevealedCard(JsonPath.read(body, "$.pan"), JsonPath.read(body, "$.expiryMonth"),
                JsonPath.read(body, "$.expiryYear"), JsonPath.read(body, "$.cvv"));
    }

    private CardCheck check(RevealedCard card, long amountMinor, String mcc) {
        return cardApi.checkForPayment(card.pan(), card.expiryMonth(), card.expiryYear(), Money.eur(amountMinor), mcc);
    }

    private record RevealedCard(String pan, int expiryMonth, int expiryYear, String cvv) {
    }
}