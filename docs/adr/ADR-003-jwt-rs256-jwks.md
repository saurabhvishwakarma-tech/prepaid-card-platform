# ADR-003: RS256 JWTs issued by core, verified through JWKS

**Status:** Accepted (2026-11-07)

## Context
Every request passes through the gateway, and core must also enforce roles per endpoint. Both need to trust the same tokens without a shared secret that could leak from either place.

## Options considered
1. HS256 (shared secret): simple, but every verifier holds the signing secret, so any of them could mint tokens.
2. Opaque session tokens checked against core on every request: easy to revoke, but each request costs a call to core.
3. RS256: core signs with a private key it alone holds and publishes the public key at a JWKS endpoint.

## Decision
Option 3. The identity module signs 15-minute access tokens (`sub` = user ID, `roles`, `iss`, `exp`, `jti`) and publishes `/.well-known/jwks.json`. Core verifies its own tokens with Spring Security's resource server; the gateway will verify them through the JWKS endpoint.

Library: Spring Security's built-in JWT support (Nimbus) instead of JJWT. It signs, verifies and produces the standard JWKS document, so there is no hand-written authentication filter.

## Consequences
- Only core can issue tokens; leaking the public key is harmless.
- Verifiers need no call to core per request; they cache the JWKS.
- The key ID (`kid`) is the key's thumbprint, so a new key can be added to the JWKS before the old one is retired.

## Risks
- Tokens cannot be revoked before they expire; mitigated by the 15-minute lifetime. No refresh tokens yet.
- The private key is a file read at startup (`keys/`, git-ignored). Production would use a secrets manager or KMS.