package com.cardplatform.core.funding.controller;

import com.cardplatform.core.funding.provider.ProviderEvent;
import com.cardplatform.core.funding.provider.StripeWebhookVerifier;
import com.cardplatform.core.funding.service.FundingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

// No user token here: Stripe proves who it is with the signature instead
@RestController
@RequestMapping("/api/core/webhooks")
public class StripeWebhookController {

    private final StripeWebhookVerifier verifier;
    private final FundingService funding;

    public StripeWebhookController(StripeWebhookVerifier verifier, FundingService funding) {
        this.verifier = verifier;
        this.funding = funding;
    }

    // The signature covers the exact bytes Stripe sent, so the body is read raw and never re-serialised.
    // Any 2xx tells Stripe to stop retrying; an error makes it try again later.
    @PostMapping("/stripe")
    public ResponseEntity<Void> stripe(@RequestBody byte[] body,
                                       @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        ProviderEvent event = verifier.verify(new String(body, StandardCharsets.UTF_8), signature);
        funding.handleWebhook(event);
        return ResponseEntity.ok().build();
    }
}