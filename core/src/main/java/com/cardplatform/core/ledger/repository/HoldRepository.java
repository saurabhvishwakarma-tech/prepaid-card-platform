package com.cardplatform.core.ledger.repository;

import com.cardplatform.core.ledger.model.Hold;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface HoldRepository extends JpaRepository<Hold, UUID> {

    Optional<Hold> findByReference(String reference);

    // Capture and release of the same hold must not run at the same time
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Hold h where h.reference = :reference")
    Optional<Hold> findByReferenceForUpdate(@Param("reference") String reference);
}