package com.cardplatform.history.repository;

import com.cardplatform.history.model.Transaction;
import com.cardplatform.history.model.TransactionStatus;
import com.cardplatform.history.model.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByTypeAndReference(TransactionType type, String reference);

    boolean existsByTypeAndReference(TransactionType type, String reference);

    List<Transaction> findByUserIdAndStatusAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
            UUID userId, TransactionStatus status, Instant from, Instant to);
}