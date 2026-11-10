package com.cardplatform.core.payments.dto;

public record ReverseCommand(String reversalId, String networkTxnId) {
}