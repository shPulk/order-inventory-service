# Decisions Log
Status: Accepted | Proposed. Format: decision, why, cost.

## Accepted
- **D-001 Boot 3.5.x, not 4.x.** Brief says 3.x; Boot 4 has breaking changes. Cost: hand-built skeleton, local Maven.
- **D-002 Schema in `schema.sql`, Hibernate `validate`.** Explicit, reviewable DB constraints (`reserved <= on_hand`). Cost: manual upkeep; Flyway in production.
- **D-003 Transitions enforced in `Order.transitionTo()`** via a table in `OrderStatus`. Cannot be bypassed; unit-tested. Cost: no per-transition hooks.
- **D-004 Timestamps are `TIMESTAMP WITH TIME ZONE`.** Hibernate 6 maps `Instant` to it; plain `TIMESTAMP` failed validation.
- **D-005 Stock = `on_hand` + `reserved` counters plus reservation rows.** Release is exact; cancel is idempotent via ACTIVE to RELEASED.
- **D-006 Allocation: warehouse with the most available stock that fits the whole line; ties to the lowest row id; no split.** Cost: may reject an order that total stock across warehouses could cover.
- **D-007 Default locking: pessimistic** (`oims.reservation.strategy=pessimistic`); optimistic (`@Version` + bounded retry) is the alternative. Measured (pool 10, 100 threads vs 10 units, single runs): both reserved exactly 10, rejected 90, failed 0, never oversold. Race time ~95-107 ms pessimistic vs ~116-117 ms optimistic, within noise. Not yet measured: pool 100, fewer retries, sustained load (Day 4).
- - Why pessimistic: it queues instead of retrying, and rows locked in sorted id order cannot deadlock. Cost: locks rows it may not use.
- **D-008 Multi-instance expiry: per-order row lock + re-check, no ShedLock.** Second instance finds the order already canceled and does nothing. Cost: instances duplicate the scan of expired ids.
- **D-009 (superseded) `customerId` comes from the JWT subject; no body field required; one sent in the body is ignored.**
- **D-010 Price snapshot on order lines.** Later price changes must not alter past orders.
- **D-011 Optional `Idempotency-Key`, unique column.** Replay returns 200 with the original order; concurrent duplicate returns 409. Cost: replay does not compare request bodies.
- **D-012 RFC 7807 errors:** 400 validation, 404 missing, 409 conflict/illegal transition, 422 business rule.
- **D-013 Status endpoint restricted to WAREHOUSE_OPS/ADMIN.** Cost: PAID can still be set without payment evidence.
- **D-014 Embedded Tomcat, file-based H2.** Allows two instances on one DB for the expiry demo.
- **D-015 Out-of-stock orders are saved as FAILED, then the API returns 422.** Audit trail. Cost: failed attempts create rows.
- **D-016 Cancellation locks the order row first, then stock rows in id order.** Cancel twice returns 200 with the same result. Late payment is rejected (422) even before the job runs.
- **D-017 JWT resource server, HS256 shared secret, roles from a `roles` claim.** No IdP in the brief. Dev-only `/api/dev/token` (flag `oims.security.dev-tokens`).
- **D-018 Another customer's order returns 404, not 403.** Prevents probing for order ids.
- **D-019 `AccessDeniedException` mapped to 403 in the global handler.** Otherwise, the catch-all returns 500.
- **D-020 Events via Spring `@TransactionalEventListener(AFTER_COMMIT)`, in-process.** Brief requires "learn about changes via events" but names no broker or guarantee. A rollback emits nothing. Cost: see debt.
- **D-021 Hikari pool 30 (configurable via `DB_POOL`).** Load test on order placement, 100 users, one laptop (app, load generator and H2 share the CPU):
- - pool 10: ~4,070 req/s, p50 17.8-21.3 ms, p99 86-96 ms;
- - pool 30: 4,817 req/s, p50 18.2, p99 56 ms;
- - pool 50: 4,632 req/s, p50 18.5, p99 65 ms.
- - p50 was flat and CPU was at 0% idle in every run, so CPU saturation, not the pool, is the first bottleneck.
- **D-022 Load test baseline (item 9).** 20 users: 4,818 req/s, p50 2.8, p95 11.9, p99 23.5 ms, all 201. Throughput stays ~4,000-4,800 req/s from 20 to 200 users while p50 grows with users (2.8 to 38 ms), so the system saturates early and extra users only queue.
- **D-023 With 100% of traffic on one product's stock rows (50 users), throughput fell about 27% (4,763 to 3,472 req/s) and p99 was no worse (37.8 vs 51.4 ms). Pessimistic locking holds up under a hot row at this scale.

## Proposed
(none open, the outbox upgrade for D-020 is listed under debt)


## Technical Debt
- OpenAPI declares a bearer scheme, but error responses (400/403/404/409/422) still show "Undocumented"; fix with one `OpenApiCustomizer`.
- Idempotency ignores body changes (D-011).
- No correlation id in logs/errors (Day 4).
- `setOnHand` is absolute; concurrent edits give 409. Prefer deltas.
- Stock is never consumed: reservations stay ACTIVE after SHIPPED, so `on_hand` never decreases.
- Expiry sweeps 100 orders per tick; a backlog drains over several ticks.
- Two-instance expiry safety is proven with threads in one JVM, not two separate JVMs.
- Events are non-durable: if the JVM dies after commit and before the listener runs, the event is lost. No retry, dead-letter or broker. Planned fix: transactional outbox + poller + idempotent consumers.
- Listeners run synchronously on the request thread, so a slow downstream slows the HTTP call (Day 5 drill; fix `@Async` or outbox).
- Only a notification listener exists; billing and shipping would be more listeners.
- Dev token endpoint is unauthenticated: anyone can mint an ADMIN token. Must be off outside local/demo.
- HS256 shared secret; no issuer/audience validation, no revocation, no refresh.
- No `GET /api/orders` ("my orders") listing.
- H2 console, Swagger and actuator health are open without a token.
- **Item 9 fix is partial.** The first bottleneck is CPU per request (0% idle, app JVM ~660%). Raising the pool did not fix it. Not done: profiling (thread dumps or IntelliJ profiler) and reducing per-request work. Candidates, unmeasured: JWT signature validation per request, Hibernate flush/dirty checking, JSON serialisation, H2 in the same JVM.