package com.cardplatform.core.payments.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("cardplatform.network")
public record NetworkClientProperties(
        @NotBlank String clientId,
        // Long and random: it is compared directly, not slow-hashed like a user password
        @NotBlank @Size(min = 32) String clientSecret) {

    @Override
    public String toString() {
        return "NetworkClientProperties[clientId=" + clientId + ", clientSecret=<hidden>]";
    }
}