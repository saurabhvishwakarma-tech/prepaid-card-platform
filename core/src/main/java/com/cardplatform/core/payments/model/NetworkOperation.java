package com.cardplatform.core.payments.model;

import com.cardplatform.core.payments.dto.OperationOutcome;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

// A processed clearing, reversal or refund, kept so a retry gets the same answer instead of a second run
@Entity
@Table(schema = "payments", name = "network_operations")
public class NetworkOperation {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private OperationType operationType;

    private String operationId;

    private String networkTxnId;

    private Long amountMinor;

    private boolean accepted;

    @Enumerated(EnumType.STRING)
    private OperationRejectReason rejectReason;

    private Instant createdAt;

    protected NetworkOperation() {
    }

    public static NetworkOperation record(OperationType type, String operationId, String networkTxnId,
                                          Long amountMinor, OperationOutcome outcome) {
        NetworkOperation operation = new NetworkOperation();
        operation.id = UUID.randomUUID();
        operation.operationType = type;
        operation.operationId = operationId;
        operation.networkTxnId = networkTxnId;
        operation.amountMinor = amountMinor;
        operation.accepted = outcome.accepted();
        operation.rejectReason = outcome.rejectReason();
        operation.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return operation;
    }

    public OperationOutcome outcome() {
        return accepted ? OperationOutcome.ACCEPTED : OperationOutcome.rejected(rejectReason);
    }
}