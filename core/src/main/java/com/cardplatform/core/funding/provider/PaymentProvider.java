package com.cardplatform.core.funding.provider;

import com.cardplatform.core.funding.model.TopUp;

// What funding needs from a card payment provider: Stripe in the app, a fake in tests
public interface PaymentProvider {

    // Charges the top-up's payment method. Calling it again for the same top-up returns the first payment.
    ProviderPayment charge(TopUp topUp);

    ProviderPayment retrieve(String paymentId);
}