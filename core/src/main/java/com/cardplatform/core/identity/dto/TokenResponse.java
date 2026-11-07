package com.cardplatform.core.identity.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}