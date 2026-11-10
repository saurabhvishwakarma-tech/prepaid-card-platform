package com.cardplatform.simulator.dto;

// decision is APPROVED, DECLINED or TIMED_OUT
public record PurchaseResponse(String networkTxnId, String decision, String authCode, String declineReason,
                               int attempts, long latencyMs) {
}