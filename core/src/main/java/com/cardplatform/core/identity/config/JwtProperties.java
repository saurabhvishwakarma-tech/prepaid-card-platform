package com.cardplatform.core.identity.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("cardplatform.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl,
        @NotNull Resource privateKeyLocation,
        @NotNull Resource publicKeyLocation) {
}