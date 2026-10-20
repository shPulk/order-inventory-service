# Decisions Log

## Accepted
- D-001 - Transitions enforced in Order.transition.To().
- D-002 - Timestamps are TIMESTAMPS WITH TIME ZONE. Hibernate 6 maps Instant to it.
- D-003 - customerId from request body is temporary, will come from JWT later.

## Proposed
- D-004 - Stock = (onHand + reserved) counters plus reservation rows.

## Technical Debt
- OpenAPI Swagger responses show "Undocumented", fix with one OpenApiCustomizer.
- Idempotency ignores body changes.
- No correlation id in the logs/errors
 