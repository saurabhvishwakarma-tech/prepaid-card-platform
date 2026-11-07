package com.cardplatform.core.identity.api;

import java.util.Optional;
import java.util.UUID;

// The only way other core modules may read identity data.
public interface IdentityApi {

    Optional<UserSummary> findUser(UUID userId);
}