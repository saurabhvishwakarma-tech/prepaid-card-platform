package com.cardplatform.core.card.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("cardplatform.card")
public record CardProperties(
        @NotBlank @Pattern(regexp = "\\d{6}") String bin,
        @NotBlank String panEncryptionKey,
        @NotBlank String panHashKey,
        @NotBlank String cvvKey) {

    // Records print every field by default; keys must never end up in a log
    @Override
    public String toString() {
        return "CardProperties[bin=" + bin + ", keys=<hidden>]";
    }
}