package com.cardplatform.core.ledger.repository;

import com.cardplatform.core.ledger.model.Posting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PostingRepository extends JpaRepository<Posting, UUID> {

    List<Posting> findByJournalEntryId(UUID journalEntryId);
}