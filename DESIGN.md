# Design

## 1. Scope

A retailer sells products from several warehouses. Customers place orders; stock must be reserved without overselling, even under concurrent load; orders follow a lifecycle; downstream systems (billing, shipping, notifications) learn about changes through events.

## 2. Architecture

Controllers (order, product, stock, report) call services (`OrderService`, `OrderCancellationService`, `ReservationService`, `ReportService`) which use Spring Data repositories over H2.

- **Events:** `OrderEvents` publishes an `OrderEvent` inside the order transaction; `NotificationListener` receives it after commit, on a bounded async executor.
- **Security:** JWT resource server, with method-level `@PreAuthorize`.
- **Background work:** a scheduled job expires unpaid reservations.

## 3. Order lifecycle

`CREATED -> RESERVED -> PAID -> SHIPPED -> DELIVERED`

- `CANCELLED` is reachable from CREATED, RESERVED and PAID.
- `FAILED` is reachable from CREATED (out of stock).
- The rules live only in `Order.transitionTo()` backed by a table in `OrderStatus` (D-003). An illegal transition returns 409.

## 4. Data model

| Table | Notes |
|---|---|
| `product`, `warehouse` | Catalog |
| `stock_level` | `on_hand`, `reserved`, `version`; unique per warehouse and product |
| `orders`, `order_line` | Lines carry a price snapshot (D-010) |
| `reservation` | One row per allocation; status ACTIVE, RELEASED or CONSUMED |

Database CHECK constraints (`reserved <= on_hand`) are the last line of defence against overselling (D-002).

## 5. Placing an order

1. Look up the `Idempotency-Key`; a replay returns the original.
2. Validate lines and snapshot prices.
3. Lock all candidate stock rows with `SELECT ... FOR UPDATE ORDER BY id`. A fixed order prevents deadlocks.
4. Plan in memory per line: pick the warehouse with the most available stock that fits the whole line (D-006). If any line cannot be covered, change nothing.
5. Apply: increase `reserved`, insert reservation rows, set RESERVED and `expires_at`. On shortfall, save the order as FAILED and return 422 (D-015).

Optimistic mode (`@Version` plus bounded retry) and pessimistic mode are the alternative strategies (D-007, D-027).

## 6. Cancellation and expiry

- Cancel locks the order row, then the stock rows in id order, and releases only ACTIVE reservations. A repeat call is a no-op (D-016).
- The expiry job lists expired order IDs, then expires each order in its own transaction that re-checks status under the row lock. Two instances cannot double-release (D-008).
- Paying after `expires_at` is rejected even if the job has not run.

## 7. Concurrency evidence

100 threads competing for 10 units: exactly 10 reserved, 90 rejected, no negative stock, for both strategies.

- Pessimistic is the default because it queues instead of retrying (D-007).
- Hot-row load test: no throughput collapse (D-023).

## 8. Security

- HS256 JWT with roles from a `roles` claim (D-017).
- Customers read and cancel only their own orders; others get 404 so IDs cannot be probed (D-018).
- Staff-only: status changes, stock, reports.
- Access denial maps to 403 (D-019).

## 9. Events

Events are in-process, delivered after commit, and non-durable (D-020). Listeners run on a bounded async executor so a slow downstream does not slow requests (D-025). The planned upgrade is a transactional outbox with idempotent consumers.

## 10. Performance

About 4,800 req/s at 20 users; the system saturates on CPU around 100 users on a single laptop; pool size and lock contention are not the first bottleneck (D-021 to D-023). These numbers show the shape of the curve, not production capacity.

## 11. Failure drills

See `DRILLS.md`.

- **Slow downstream** fixed (510 ms to 3 ms).
- **Duplicate-request race** documented, fix designed.

## 12. Known limitations

See the technical debt section of `DECISIONS.md`. The main ones:

- Non-durable events
- Stock never consumed on shipment
- Idempotency race for concurrent retries
- Partial item 9 fix
- Dev token endpoint and shared-secret JWT