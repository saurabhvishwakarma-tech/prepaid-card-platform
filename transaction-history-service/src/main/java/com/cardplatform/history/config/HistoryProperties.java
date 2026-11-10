package com.cardplatform.history.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("cardplatform.history")
public record HistoryProperties(@NotBlank String jwkSetUri, @NotBlank String issuer) {
}