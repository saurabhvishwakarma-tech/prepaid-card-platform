package com.cardplatform.simulator.dto;

// result is ACCEPTED or REJECTED
public record OperationResponse(String networkTxnId, String operationId, String result, String rejectReason,
                                int attempts) {
}