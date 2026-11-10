package com.cardplatform.core.payments.dto;

import com.cardplatform.core.payments.model.OperationRejectReason;

public record OperationOutcome(boolean accepted, OperationRejectReason rejectReason) {

    public static final OperationOutcome ACCEPTED = new OperationOutcome(true, null);

    public static OperationOutcome rejected(OperationRejectReason reason) {
        return new OperationOutcome(false, reason);
    }
}