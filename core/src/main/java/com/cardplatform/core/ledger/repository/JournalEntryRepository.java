package com.cardplatform.core.ledger.repository;

import com.cardplatform.core.ledger.model.EntryType;
import com.cardplatform.core.ledger.model.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    boolean existsByEntryTypeAndReference(EntryType entryType, String reference);

    Optional<JournalEntry> findByEntryTypeAndReference(EntryType entryType, String reference);
}