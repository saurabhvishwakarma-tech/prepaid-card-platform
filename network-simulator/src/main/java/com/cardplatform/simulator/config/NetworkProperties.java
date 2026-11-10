package com.cardplatform.simulator.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("simulator.network")
public record NetworkProperties(
        @NotBlank String clientId,
        @NotBlank @Size(min = 32) String clientSecret,
        @NotNull Duration deadline,
        @Min(1) @Max(5) int maxAttempts,
        @NotNull Duration retryBackoff) {

    @Override
    public String toString() {
        return "NetworkProperties[clientId=" + clientId + ", clientSecret=<hidden>, deadline=" + deadline
                + ", maxAttempts=" + maxAttempts + ", retryBackoff=" + retryBackoff + "]";
    }
}