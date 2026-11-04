# ADR-001: One Postgres container; schema per module in core; database per satellite

**Status:** Accepted (2026-11-04)

## Context
Each app must own its data, but running one Postgres container per app costs memory the local machine does not have.

## Options considered
1. One Postgres container per app: strongest isolation, most memory.
2. One container, one shared database: cheapest, but nothing stops apps reading each other's tables.
3. One container; `core_db` with one schema per core module; a separate database and login user per satellite.

## Decision
Option 3. Databases `core_db`, `history_db`, `rewards_db`, `notification_db`, each owned by its own user, with all privileges (including CONNECT) revoked from PUBLIC so a user can connect only to its own database. Inside `core_db`, schemas `identity`, `ledger`, `card`, `payments`, `funding` and `messaging` are created by Flyway, not by the init script.

## Consequences
- Isolation between apps is enforced by Postgres permissions, not just convention.
- Core modules share one database, which is what allows a payment to be one transaction across ledger, card and payments.
- The init script runs only on an empty volume; changing users or databases later needs `docker compose down -v` locally or a manual migration.

## Risks
- One container is a single point of failure for every app. Acceptable locally; production would use separate managed instances.
- Schema boundaries inside `core_db` are not enforced by permissions, only by the module boundary test.