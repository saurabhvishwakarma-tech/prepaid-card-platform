## Context
Customers need to load real (test-mode) money onto their account with a card. The platform must never credit money it didn't receive, never credit twice, and never hold card numbers itself.

## Options considered
1. Credit the ledger as soon as Stripe's create call returns `succeeded`: simplest, but trusts one HTTP response that can time out after the charge happened.
2. Credit only from Stripe's signed `payment_intent.succeeded` webhook, with a reconciliation job for webhooks that never arrive.
3. Poll Stripe for every top-up: no webhook endpoint, but slow and many API calls.

## Decision
Option 2. Core creates a PaymentIntent with Stripe's idempotency key `topup-<id>`, so a retry never charges twice. Our own `Idempotency-Key` header is unique per user in Postgres. The webhook is verified with Stripe's HMAC signature and a 5-minute timestamp tolerance, stored by event ID and applied once. The ledger entry's reference is `topup-<id>`, so even two different events for one payment credit once. A job asks Stripe about top-ups still pending after 5 minutes. Only test-mode keys are accepted.

## Consequences
- Card numbers never touch the platform; only Stripe's `pm_...` and `pi_...` references are stored.
- A top-up shows `PENDING` for a moment before `COMPLETED`.
- The webhook is public; its only protection is the signature, which is checked before anything else.

## Risks
- No saved cards or Stripe customers yet: each top-up names its payment method.
- Cards needing 3-D Secure fail, because there is no page to show the challenge.
- A top-up stuck in `CREATED` (crash between saving it and reaching Stripe) is only resumed by the client retrying with the same key.
- No refunds of top-ups.