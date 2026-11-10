package com.cardplatform.core.card.dto;

// Full card details: returned only by the reveal endpoint and never logged
public record CardDetailsResponse(String pan, int expiryMonth, int expiryYear, String cvv) {
}