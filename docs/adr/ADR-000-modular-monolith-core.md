# ADR-000: Modular monolith core with event-driven satellite services

**Status:** Accepted (2026-11-04)

## Context
The platform moves money: authorisation, card checks, holds and the ledger must succeed or fail together. The first design split this across ten microservices, which made every payment a distributed transaction and needed more infrastructure than one developer and a 7.5 GB Docker budget can run.

## Options considered
1. Ten microservices, one per domain: strong isolation, but a payment spans several services, needs sagas or two-phase commit, and every hop is a new failure mode.
2. A single monolith: simplest, but history, rewards and notifications would share the payment's transaction and runtime, so a slow report could slow a payment.
3. A modular monolith core for everything that changes money, plus separate services for everything that reacts to it.

## Decision
Option 3. `card-platform-core` holds identity, ledger, card, payments and funding as modules with enforced boundaries (each module exposes only its `api` package). Transaction history, rewards and notification are separate services that consume Kafka events. The network simulator and the gateway are separate because they are external or edge concerns.

Rule for future features: if it changes money or core state, it becomes a core module; if it reacts to something that already happened, it becomes a new Kafka-consuming service.

## Consequences
- A card authorisation is one local ACID transaction: no distributed transactions.
- Satellites can be slow, down or redeployed without affecting payments.
- Six deployables instead of ten: fewer moving parts to run locally.
- Core modules scale together, which is acceptable at this size.

## Risks
- Module boundaries can erode inside one codebase. Mitigation: an automated boundary test (Day 3).
- Core is a larger single deployable; a bad core release affects all money flows.