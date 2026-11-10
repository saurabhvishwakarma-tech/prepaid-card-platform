# ADR-004: Idempotency in Postgres; Redis for rate limiting only

**Status:** Accepted (2026-11-07)

## Context
Networks retry authorisations, Stripe resends webhooks, clients resend top-ups, and Kafka delivers at least once. Every money-moving operation will sometimes arrive twice, possibly at the same moment.

## Options considered
1. Idempotency keys in Redis with a TTL: fast, but a separate store from the money, so a crash between "key saved" and "money moved" leaves them disagreeing, and keys expire.
2. Check-then-insert in application code only: two concurrent duplicates can both pass the check.
3. Unique constraints in Postgres, in the same transaction as the money movement.

## Decision
Option 3. The ledger enforces `UNIQUE (entry_type, reference)` on journal entries and `UNIQUE (reference)` on holds. The check for an existing record runs after the account row is locked, so a concurrent duplicate waits and then sees the first one; the constraint is the final guarantee. Later modules follow the same rule (authorisations on `network_txn_id`, top-ups on user + `Idempotency-Key`, processed Kafka event IDs per consumer). Redis is used only for gateway rate-limit counters.

## Consequences
- A duplicate can never move money twice, even across restarts.
- The proof of idempotency lives next to the money, in one transaction.
- No expiry: references are kept as long as the ledger is.

## Risks
- Every money-moving caller must supply a stable reference; a caller that generates a new one per retry defeats the protection.
- Locking the account row serialises payments per account, and the shared system accounts are locked by every capture. Fine at this scale; a busy production ledger would spread system postings over several accounts or skip a cached balance for them.