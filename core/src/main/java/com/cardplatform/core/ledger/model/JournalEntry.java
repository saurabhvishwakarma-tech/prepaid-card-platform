package com.cardplatform.core.ledger.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

// Append-only: entries are never updated or deleted, mistakes are corrected with new entries.
@Entity
@Table(schema = "ledger", name = "journal_entries")
public class JournalEntry {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private EntryType entryType;

    private String reference;

    private Instant createdAt;

    protected JournalEntry() {
    }

    public static JournalEntry create(EntryType entryType, String reference) {
        JournalEntry entry = new JournalEntry();
        entry.id = UUID.randomUUID();
        entry.entryType = entryType;
        entry.reference = reference;
        entry.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return entry;
    }

    public UUID getId() {
        return id;
    }
}