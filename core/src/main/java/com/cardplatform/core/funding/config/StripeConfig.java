package com.cardplatform.core.funding.config;

import com.stripe.StripeClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class StripeConfig {

    // A client instance rather than the SDK's global static key, so nothing else in the JVM can pick the key up
    @Bean
    StripeClient stripeClient(StripeProperties properties) {
        return new StripeClient(properties.secretKey());
    }
}