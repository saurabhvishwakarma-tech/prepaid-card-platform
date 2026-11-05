package com.cardplatform.contracts;

import com.cardplatform.contracts.common.v1.EventMetadata;
import com.cardplatform.contracts.common.v1.Merchant;
import com.cardplatform.contracts.common.v1.MerchantCategory;
import com.cardplatform.contracts.common.v1.Money;
import com.cardplatform.contracts.events.v1.PaymentAuthorized;
import com.cardplatform.contracts.events.v1.PaymentEvent;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UnknownFieldSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractsSerializationTest {

    @Test
    void paymentEventSurvivesRoundTrip() throws InvalidProtocolBufferException {
        PaymentEvent original = PaymentEvent.newBuilder()
                .setMetadata(EventMetadata.newBuilder()
                        .setEventId("7f1c2b9e-0d4a-4c1e-9a55-3e2f6b8d1a01")
                        .setOccurredAt(Timestamp.newBuilder().setSeconds(1_762_300_000L)))
                .setAccountId("acc-1")
                .setCardId("card-1")
                .setNetworkTxnId("ntx-1")
                .setMerchant(Merchant.newBuilder()
                        .setMerchantId("m-1")
                        .setName("Corner Shop")
                        .setMcc("5411")
                        .setCountry("IE"))
                .setCategory(MerchantCategory.MERCHANT_CATEGORY_GROCERIES)
                .setAuthorized(PaymentAuthorized.newBuilder()
                        .setAmount(Money.newBuilder().setAmountMinor(1250).setCurrency("EUR"))
                        .setAuthCode("A1B2C3"))
                .build();

        PaymentEvent parsed = PaymentEvent.parseFrom(original.toByteArray());

        assertEquals(original, parsed);
        assertEquals(PaymentEvent.EventCase.AUTHORIZED, parsed.getEventCase());
        assertEquals(1250, parsed.getAuthorized().getAmount().getAmountMinor());
    }

    // Proves the additive-only rule works: an older consumer reading a message
    // from a newer producer keeps the fields it knows and skips the new one.
    @Test
    void fieldUnknownToReaderIsSkippedNotRejected() throws InvalidProtocolBufferException {
        UnknownFieldSet fieldFromNewerSchema = UnknownFieldSet.newBuilder()
                .addField(99, UnknownFieldSet.Field.newBuilder().addVarint(42).build())
                .build();
        byte[] bytes = Money.newBuilder()
                .setAmountMinor(500)
                .setCurrency("EUR")
                .setUnknownFields(fieldFromNewerSchema)
                .build()
                .toByteArray();

        Money parsed = Money.parseFrom(bytes);

        assertEquals(500, parsed.getAmountMinor());
        assertEquals("EUR", parsed.getCurrency());
        assertTrue(parsed.getUnknownFields().hasField(99));
    }
}