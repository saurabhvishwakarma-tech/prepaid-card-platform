package com.cardplatform.core.identity.service;

import java.time.Duration;

public record AccessToken(String value, Duration ttl) {
}