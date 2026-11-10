package com.cardplatform.core.shared.outbox.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxMessage, UUID> {

    List<OutboxMessage> findTop100ByPublishedAtIsNullOrderBySeqAsc();

    @Transactional
    @Modifying
    @Query("update OutboxMessage m set m.publishedAt = :publishedAt where m.id = :id")
    void markPublished(@Param("id") UUID id, @Param("publishedAt") Instant publishedAt);
}