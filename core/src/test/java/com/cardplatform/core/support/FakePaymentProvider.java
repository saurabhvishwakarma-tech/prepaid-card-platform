package com.cardplatform.core.support;

import com.cardplatform.core.funding.exception.PaymentDeclinedException;
import com.cardplatform.core.funding.exception.PaymentProviderUnavailableException;
import com.cardplatform.core.funding.model.TopUp;
import com.cardplatform.core.funding.provider.PaymentProvider;
import com.cardplatform.core.funding.provider.ProviderPayment;
import org.springframework.context.annotation.Primary;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// Stands in for Stripe: same test payment methods, same "one payment per idempotency key" behaviour
@Primary
public class FakePaymentProvider implements PaymentProvider {

    public static final String DECLINED_CARD = "pm_card_chargeDeclined";

    private final Map<String, ProviderPayment> payments = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicInteger> charges = new ConcurrentHashMap<>();

    @Override
    public ProviderPayment charge(TopUp topUp) {
        charges.computeIfAbsent(topUp.getId(), id -> new AtomicInteger()).incrementAndGet();
        if (DECLINED_CARD.equals(topUp.getPaymentMethodId())) {
            throw new PaymentDeclinedException("generic_decline");
        }
        ProviderPayment payment = new ProviderPayment(paymentIdFor(topUp.getId()), "succeeded",
                topUp.getAmountMinor(), topUp.getCurrency(), topUp.getId());
        payments.put(payment.id(), payment);
        return payment;
    }

    @Override
    public ProviderPayment retrieve(String paymentId) {
        ProviderPayment payment = payments.get(paymentId);
        if (payment == null) {
            throw new PaymentProviderUnavailableException();
        }
        return payment;
    }

    public String paymentIdFor(UUID topUpId) {
        return "pi_fake_" + topUpId.toString().replace("-", "");
    }

    public int chargesFor(UUID topUpId) {
        AtomicInteger count = charges.get(topUpId);
        return count == null ? 0 : count.get();
    }
}