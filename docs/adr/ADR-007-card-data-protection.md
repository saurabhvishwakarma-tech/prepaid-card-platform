## Options considered
1. Store the PAN in plain text: simple lookups, unacceptable exposure.
2. Encrypt the PAN only: protected at rest, but finding a card by number means decrypting every row.
3. Encrypt the PAN with AES-GCM and store a keyed HMAC of it for lookups; derive the CVV instead of storing it.

## Decision
Option 3. Three separate 256-bit keys from the environment: one for AES-GCM encryption (card ID bound as associated data), one for the HMAC lookup hash, one for CVV derivation. Only the last four digits appear in API responses and logs; full details come only from an authenticated POST reveal endpoint with `Cache-Control: no-store`, and each reveal is logged by card ID.

## Consequences
- A database dump alone reveals no card numbers or CVVs.
- Lookup by number is a single indexed query on the HMAC.
- Keys can't be changed casually: existing ciphertexts depend on them.

## Risks
- Keys live in `.env`, readable by anyone with access to the machine. Production would use a KMS or HSM with key rotation and re-encryption.
- An attacker with both the database and the HMAC key could try all card numbers in the 990001 range; the key must be protected as carefully as the encryption key.
- This is a simulation: no PCI DSS scope, network tokenisation or real CVV algorithm.