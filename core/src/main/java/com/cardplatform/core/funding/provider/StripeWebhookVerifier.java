package com.cardplatform.core.funding.provider;

import com.cardplatform.core.funding.config.StripeProperties;
import com.cardplatform.core.funding.exception.InvalidWebhookException;
import com.google.gson.JsonParseException;
import com.stripe.StripeClient;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import org.springframework.stereotype.Component;

@Component
public class StripeWebhookVerifier {

    private static final String PAYMENT_INTENT_EVENT_PREFIX = "payment_intent.";

    private final StripeClient stripe;
    private final String signingSecret;

    public StripeWebhookVerifier(StripeClient stripe, StripeProperties properties) {
        this.stripe = stripe;
        this.signingSecret = properties.webhookSecret();
    }

    public ProviderEvent verify(String payload, String signature) {
        if (signature == null || signature.isBlank()) {
            throw new InvalidWebhookException();
        }
        Event event;
        try {
            // Checks the HMAC over the exact payload, and refuses timestamps older than five minutes (replays)
            event = stripe.constructEvent(payload, signature, signingSecret);
        } catch (SignatureVerificationException | JsonParseException e) {
            throw new InvalidWebhookException();
        }
        return new ProviderEvent(event.getId(), event.getType(), paymentIn(event));
    }

    private static ProviderPayment paymentIn(Event event) {
        if (!event.getType().startsWith(PAYMENT_INTENT_EVENT_PREFIX)) {
            return null;
        }
        try {
            // "Unsafe" only means the event's API version may differ from the SDK's;
            // the few fields read here are the same in every version
            return StripePaymentProvider.toPayment(
                    (PaymentIntent) event.getDataObjectDeserializer().deserializeUnsafe());
        } catch (EventDataObjectDeserializationException e) {
            throw new IllegalStateException("Unreadable payment in Stripe event " + event.getId(), e);
        }
    }
}