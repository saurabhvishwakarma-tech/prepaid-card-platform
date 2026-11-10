## Context
Core must tell other services about payments and ledger postings through Kafka. Saving to Postgres and sending to Kafka are two separate systems: a crash between them either loses an event or publishes one for a change that rolled back.

## Options considered
1. Send to Kafka inside the business method: a rollback after sending leaves a phantom event, a crash before sending loses it.
2. Kafka transactions combined with database transactions: no atomic commit across both, so the gap remains.
3. Change data capture (Debezium) on the database log: reliable, but adds Kafka Connect and another set of containers.
4. Transactional outbox: write the event to an outbox table in the same database transaction, and publish it from there.

## Decision
Option 4. Modules write events through `OutboxWriter`, which requires an existing transaction (`MANDATORY`). A scheduled relay reads unpublished rows in insert order (`seq`), sends each to Kafka keyed by account, waits for the broker's acknowledgement (`acks=all`), and only then marks it published. On a send failure it stops the batch, so one account's events are never reordered, and retries on the next poll.

## Consequences
- A committed change always produces its event; a rolled-back change never does.
- Delivery is at-least-once: a crash after Kafka stores a message but before the row is marked re-sends it. Every consumer stores processed event IDs and skips duplicates.
- Kafka being down delays events but never blocks payments.
- Events follow commit order closely but not perfectly: two transactions can commit in a different order from their `seq`. For one account this can't happen, because its row lock serialises those transactions.

## Risks
- One relay instance only: two core instances would both publish the same rows. Later: `FOR UPDATE SKIP LOCKED` or leader election.
- Published rows are never deleted yet; a cleanup job will be needed.
- Polling adds up to one poll interval (500 ms) of delay.