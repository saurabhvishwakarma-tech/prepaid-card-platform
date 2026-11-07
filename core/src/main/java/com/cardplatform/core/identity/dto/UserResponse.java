package com.cardplatform.core.identity.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String email, String fullName, String role, String kycStatus,
                           Instant createdAt) {
}