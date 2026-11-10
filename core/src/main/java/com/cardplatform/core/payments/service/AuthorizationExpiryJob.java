package com.cardplatform.core.payments.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
class AuthorizationExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationExpiryJob.class);

    private final PaymentService payments;
    private final Duration authorizationTtl;

    AuthorizationExpiryJob(PaymentService payments,
                           @Value("${cardplatform.payments.authorization-ttl}") Duration authorizationTtl) {
        this.payments = payments;
        this.authorizationTtl = authorizationTtl;
    }

    @Scheduled(fixedDelayString = "${cardplatform.payments.expiry-check-interval}")
    void expireStaleAuthorizations() {
        int expired = payments.expireAuthorizationsBefore(Instant.now().minus(authorizationTtl));
        if (expired > 0) {
            log.info("Expired {} authorizations older than {}", expired, authorizationTtl);
        }
    }
}
