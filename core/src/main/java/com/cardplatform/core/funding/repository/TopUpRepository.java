package com.cardplatform.core.funding.repository;

import com.cardplatform.core.funding.model.TopUp;
import com.cardplatform.core.funding.model.TopUpStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TopUpRepository extends JpaRepository<TopUp, UUID> {

    Optional<TopUp> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    Optional<TopUp> findByIdAndUserId(UUID id, UUID userId);

    List<TopUp> findTop50ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(TopUpStatus status, Instant before);

    // The webhook, reconciliation and the original request can all touch one top-up; they take turns
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TopUp t where t.id = :id")
    Optional<TopUp> findByIdForUpdate(@Param("id") UUID id);
}