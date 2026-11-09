package com.cardplatform.core.ledger.repository;

import com.cardplatform.core.ledger.model.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByUserIdAndCurrency(UUID userId, String currency);

    // Only the ID, so no unlocked copy of the account sits in the session before it is locked
    @Query("select a.id from Account a where a.code = :code")
    Optional<UUID> findIdByCode(@Param("code") String code);

    // Row lock: concurrent payments on one account queue here instead of both spending the same money
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);
}