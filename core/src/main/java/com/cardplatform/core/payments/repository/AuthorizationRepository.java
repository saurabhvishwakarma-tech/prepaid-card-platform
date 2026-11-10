package com.cardplatform.core.payments.repository;

import com.cardplatform.core.payments.model.Authorization;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AuthorizationRepository extends JpaRepository<Authorization, UUID> {

    Optional<Authorization> findByNetworkTxnId(String networkTxnId);

    // Clearing, reversal and refund of one authorisation must take turns
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Authorization a where a.networkTxnId = :networkTxnId")
    Optional<Authorization> findByNetworkTxnIdForUpdate(@Param("networkTxnId") String networkTxnId);
}