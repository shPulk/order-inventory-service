# Decisions Log

## Accepted
- D-001 - Transitions enforced in Order.transition.To().
- D-002 - Timestamps are TIMESTAMPS WITH TIME ZONE. Hibernate 6 maps Instant to it.
- D-003 - customerId from request body is temporary, will come from JWT later.
- D-004 - Default locking:PESSIMISTIC. Measured (10 pool, 100 threads vs 10 units, single runs): both strategies reserved exactly 10, rejected 90, failed 0, never oversold.
- - In addition to the above point, Race time ~95-107 ms pessimistic vs ~116-117 ms optimistic.
- D-005 - Stock = (onHand + reserved) counters plus reservation rows.

## Proposed


## Technical Debt
- OpenAPI Swagger responses show "Undocumented", fix with one OpenApiCustomizer.
- Idempotency ignores body changes.
- No correlation id in the logs/errors
 