# Self review

## Three weakest spots

1. **Events are not durable.** An event is lost if the JVM dies after commit, or if the async queue is full at shutdown. Billing or shipping can silently miss an order. Fix: outbox + idempotent consumers.

2. **Idempotency race.** One order is created, but 19 of 20 simultaneous retries got an error instead of the original order. Fix is designed and small; I left it unapplied because item 10 needs one fix.

3. **Stock is never consumed.** Reservations stay ACTIVE after shipment, so `on_hand` never decreases and reports drift from reality. Needs a RESERVED-to-CONSUMED step on shipment.

## Debt taken knowingly

Item 9 fix is partial (CPU bottleneck identified, not reduced); HS256 shared secret and an open dev token endpoint; H2 and unauthenticated metrics; absolute stock edits; no Flyway. All in `DECISIONS.md`.

## What I would do next

Outbox first, then consume stock on shipment, then profile the request path.