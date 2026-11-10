## Context
The card network (our simulator) calls core to authorise, clear, reverse and refund card payments. It is an external, latency-critical caller: a real network waits a few hundred milliseconds, then retries or stands in for the issuer.

## Options considered
1. REST/JSON: familiar and easy to inspect, but the contract is a convention, and every client must hand-write serialisation and deadline handling.
2. Messaging (Kafka): decoupled, but an authorisation needs an answer within the deadline, not eventually.
3. gRPC with the Protobuf contract from `contracts/`: typed request and response, generated client and server, deadlines built into every call.

## Decision
Option 3. Core exposes `CardNetworkService` on its own port (9090) using Spring Boot 4.1's gRPC server; REST stays on 8081. Every request carries a network-chosen ID, and core answers retries from stored results (Postgres unique constraints, ADR-004).

Spring Boot would secure gRPC with user JWTs by default because core has a JWT decoder. The network is a system, so core replaces that rule: only the network client (HTTP Basic credentials, compared in constant time) may call `CardNetworkService`; gRPC health and reflection stay open; everything else is denied.

## Consequences
- The contract is compiled into both sides; a mismatch fails the build.
- A call that times out on the network side may still commit in core; the network's retry then gets the stored answer, so nothing is charged twice.
- Two server ports to run and secure.

## Risks
- Credentials travel in plaintext over local gRPC. Production would use TLS, and between a network and an issuer, mutual TLS with no shared secret.
- gRPC is harder to inspect by hand than REST; tests and the simulator are the main tools.