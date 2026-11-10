# ADR-002: Protobuf contracts shared by gRPC and Kafka

**Status:** Accepted (2026-11-05)

## Context
Six apps exchange messages: the network simulator calls core over gRPC, and core publishes events that three services consume from Kafka. If each app defines its own copy of a message, the copies drift apart and break at runtime.

## Options considered
1. JSON with Java classes copied into each app: readable, but no shared schema and nothing stops a field being renamed on one side only.
2. JSON Schema or Avro with a schema registry: strong evolution rules, but adds a registry container and a second toolchain alongside gRPC.
3. Protobuf in one `contracts` module, used by both gRPC and Kafka.

## Decision
Option 3. All `.proto` files live in `contracts/`, versioned by package (`cardplatform.<area>.v1`). Each Kafka topic carries one envelope message with shared fields and a `oneof` for the event type. The module uses `spring-boot-starter-parent` so protoc, the gRPC code generator and the runtime libraries stay on the versions Spring Boot manages.

## Consequences
- One definition per message; a breaking change fails at compile time in every app that uses it.
- gRPC needs Protobuf anyway, so Kafka events add no new technology.
- Events are binary, so Kafka UI shows bytes, not readable JSON.

## Risks
- Evolution depends on discipline: fields are only added, never renumbered, reused or retyped. Removed fields must be marked `reserved`.
- No schema registry, so producer and consumer compatibility is not checked at runtime; all apps build from the same contracts version in this monorepo.