package com.cardplatform.gateway.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("cardplatform.gateway")
public record GatewaySecurityProperties(
        // Used by the routes in application.yml
        @NotBlank String coreUri,
        @NotBlank String jwkSetUri,
        @NotBlank String issuer) {
}