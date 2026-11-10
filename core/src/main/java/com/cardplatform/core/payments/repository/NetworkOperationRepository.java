package com.cardplatform.core.payments.repository;

import com.cardplatform.core.payments.model.NetworkOperation;
import com.cardplatform.core.payments.model.OperationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NetworkOperationRepository extends JpaRepository<NetworkOperation, UUID> {

    Optional<NetworkOperation> findByOperationTypeAndOperationId(OperationType operationType, String operationId);
}