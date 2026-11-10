package com.cardplatform.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.security.Principal;

@Configuration
class RateLimitConfig {

    // Authenticated traffic is limited per user, so one busy client can't use up everyone's capacity.
    // @Primary: the gateway's rate limiter takes a single default KeyResolver by type, and there are two here.
    @Bean
    @Primary
    KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .map(userId -> "user:" + userId);
    }

    // Sign-up and login happen before there is a user, so they are limited per client address
    @Bean
    KeyResolver clientIpKeyResolver() {
        return exchange -> Mono.justOrEmpty(exchange.getRequest().getRemoteAddress())
                .map(address -> "ip:" + address.getAddress().getHostAddress());
    }
}