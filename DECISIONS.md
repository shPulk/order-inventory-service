# Decisions log

Format: decision, why, cons.

## Accepted

- **D-001 Boot 3.5.x, not 4.x**: brief says 3.x; Boot 4 has breaking changes. Cons: hand-build skeleton.
- **D-002 Schema in `schema.sql`, Hibernate `validate`**: explicit, reviewable DB constraints (`reserved <= on_hand`). Cons: manual upkeep; Flyway in production.
- **D-003 Transitions enforced in `Order.transitionTo()`**: one table of allowed transitions. Cannot be bypassed by repository updates. Cons: no per-transition hooks.
- **D-004 Timestamps are `TIMESTAMP WITH TIME ZONE`**: Hibernate 6 maps `Instant` to it; plain TIMESTAMP failed validation.
- **D-005 Stock = `on_hand` + `reserved` counters plus reservation rows**: release is exact; cancel is idempotent via ACTIVE to RELEASED.
- **D-006 Allocation: warehouse with the most available stock that fits the whole line**: easy to reason from the brief. No split line. Could fragment stock across warehouses.
- **D-007 Default locking: pessimistic (`@Transactional`/`SELECT ... FOR UPDATE ORDER BY id`)**: reliable (oversell 0, window retries). `oims.reservation.strategy=optimistic` is the alternative; uses bounded retry around `@Version`. Cons: pess throughput stronger under load; optimistic can retry heavily.
- **D-008 Events: AFTER_COMMIT via Spring events**: simple and correct after commit. Not durable (server crash after commit loses an event). Upgrade path: transactional outbox or message broker.
- **D-009 Expiry: list expired IDs then lock/recheck each in a new transaction**: safe across app instances; prevents double-release. Cons: two-phase scan.
- **D-010 Spring Security JWT resource server**: roles from a `roles` claim. Dev token endpoint under local/demo only. Cons: shared secret.
- **D-011 Identity: `customerId` comes from JWT subject** (supersedes the body field): body value is ignored.
- **D-012 “Auditable” means fixed timestamp + actor plus changes made** (not full event sourcing).
- **D-013 API errors use ProblemDetail + stable error codes**: clients can branch reliably.
- **D-014 Validation: Bean Validation + line-level checks**: body/field errors are mapped consistently.
- **D-015 Robust order ID sequence**: seeded above 1,000; explicit IDs avoid H2 sequence collisions on seed rows.
- **D-016 Cancel stock updates are ordered by reservation/stock ID**: fixed locking order; avoids deadlocks. Late payment is rejected even before the expiry job runs.
- **D-017 JWT resource server uses shared secret, roles from a `roles` claim**: no IdP in the brief. `/api/dev/token` (local/demo profile) convenience only.
- **D-018 Hikari maximum-pool-size default 30, env DB_POOL override**: enough connections for load test.
- **D-019 Async event notification uses bounded `ThreadPoolTaskExecutor`** (core 2/max 4, queue 1000, `CallerRunsPolicy`): keeps slow notification off request latency; `oims.events.async=false` is available for synchronous mode. Cons: back-pressure can surface as request latency under saturation.
- **D-020 Events via Spring `@TransactionalEventListener(AFTER_COMMIT)`, in-process**: the brief requires “learn about changes via events” but makes no broker or guarantee requirement. Cons: non-durable.
- **D-021 Hikari pool 30 (`DB_POOL`), load test 100 users, 1000 iterations, DB pool 30**.
- **D-022 `ddl-auto=validate` and H2 file mode**: schema controlled by `schema.sql`; file survives app restarts.
- **D-023 Item 9 partial fix: JPA sequence allocation size 1 and explicit sequence seeding**: setting the sequence to 1000 removed collision with seeded rows.
- **D-024 Reports use plain SQL (`JdbcTemplate`)**: aggregation is simpler and more explicit.
- **D-025 Load baseline**: 20 users, 100 iterations, 5 s think time. Throughput and latency recorded in load-test evidence.
- **D-026 Load behaviour**: bottleneck under higher concurrency is CPU/DB contention rather than connection starvation.
- **D-027 Lock strategy evidence**: pessimistic locking remains the default; optimistic locking is available but retries under contention.

## Technical debt

- Events are non-durable. If the JVM dies after commit and before the listener runs, the event is lost. No retry, dead-letter or broker. Fix: transactional outbox + idempotent consumers.
- Dev token endpoint is for local/demo only and must not be enabled outside that environment.
- JWT uses a shared HMAC secret. Production should use an external identity provider / asymmetric keys and key rotation.
- Idempotency currently prevents duplicate orders through a unique constraint, but concurrent requests with the same key can cause losing requests to fail rather than replay the winner’s order.
- `DataIntegrityViolationException` handling should return the already-created order for an existing idempotency key.
- Stock is reserved but not yet consumed on shipment; reservation state transitions need to be completed for the full lifecycle.
- Item 9 is only partially addressed. Sequence collisions were fixed, but the database/seed strategy should be hardened further.
- Failure drills and performance/load evidence should continue to be kept alongside `DRILLS.md` and the load-test outputs.
- OpenAPI documentation is generated from the application and stored under `docs/openapi.yaml`; keep the exported specification in sync with API changes.