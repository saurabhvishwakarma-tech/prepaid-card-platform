package com.cardplatform.core.funding.provider;

import com.cardplatform.core.funding.exception.PaymentDeclinedException;
import com.cardplatform.core.funding.exception.PaymentProviderUnavailableException;
import com.cardplatform.core.funding.model.TopUp;
import com.stripe.StripeClient;
import com.stripe.exception.CardException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

@Component
class StripePaymentProvider implements PaymentProvider {

    static final String TOP_UP_ID = "top_up_id";

    private static final Logger log = LoggerFactory.getLogger(StripePaymentProvider.class);

    private final StripeClient stripe;

    StripePaymentProvider(StripeClient stripe) {
        this.stripe = stripe;
    }

    @Override
    public ProviderPayment charge(TopUp topUp) {
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(topUp.getAmountMinor())
                .setCurrency(topUp.getCurrency().toLowerCase(Locale.ROOT))
                .setPaymentMethod(topUp.getPaymentMethodId())
                .setConfirm(true)
                // Only methods that complete without a redirect: there's no web page to send the customer back to
                .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                        .build())
                // How the webhook and reconciliation find the top-up again
                .putMetadata(TOP_UP_ID, topUp.getId().toString())
                .build();
        // The same key on every attempt for this top-up: Stripe answers a repeat with the first payment
        RequestOptions options = RequestOptions.builder().setIdempotencyKey("topup-" + topUp.getId()).build();
        try {
            return toPayment(stripe.v1().paymentIntents().create(params, options));
        } catch (CardException e) {
            throw new PaymentDeclinedException(e.getDeclineCode() != null ? e.getDeclineCode() : e.getCode());
        } catch (InvalidRequestException e) {
            // For example an unknown payment method: retrying can't help, so the top-up fails
            throw new PaymentDeclinedException(e.getCode() != null ? e.getCode() : "invalid_request");
        } catch (StripeException e) {
            log.warn("Stripe charge failed: topUpId={}, requestId={}, code={}",
                    topUp.getId(), e.getRequestId(), e.getCode());
            throw new PaymentProviderUnavailableException();
        }
    }

    @Override
    public ProviderPayment retrieve(String paymentId) {
        try {
            return toPayment(stripe.v1().paymentIntents().retrieve(paymentId));
        } catch (StripeException e) {
            log.warn("Stripe lookup failed: paymentId={}, requestId={}, code={}",
                    paymentId, e.getRequestId(), e.getCode());
            throw new PaymentProviderUnavailableException();
        }
    }

    static ProviderPayment toPayment(PaymentIntent intent) {
        return new ProviderPayment(intent.getId(), intent.getStatus(), intent.getAmount(),
                intent.getCurrency().toUpperCase(Locale.ROOT), topUpId(intent));
    }

    // Metadata can be set by anything using the same Stripe account, so a bad value is treated as "not ours"
    private static UUID topUpId(PaymentIntent intent) {
        String value = intent.getMetadata() == null ? null : intent.getMetadata().get(TOP_UP_ID);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}