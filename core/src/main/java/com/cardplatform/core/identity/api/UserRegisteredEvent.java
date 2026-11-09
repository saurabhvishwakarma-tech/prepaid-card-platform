package com.cardplatform.core.identity.api;

import java.util.UUID;

// Published inside the registration transaction; listeners that fail roll the registration back.
public record UserRegisteredEvent(UUID userId) {
}