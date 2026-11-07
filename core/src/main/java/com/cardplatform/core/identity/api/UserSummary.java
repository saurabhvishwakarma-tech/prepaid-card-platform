package com.cardplatform.core.identity.api;

import java.util.UUID;

public record UserSummary(UUID userId, String fullName, boolean kycVerified) {
}