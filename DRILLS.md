# Failure drills (item 10)

## A. Slow downstream (fixed)
- **Trigger:** notification listener sleeps 500 ms (`oims.events.simulated-delay-ms=500`).
- **Before:** `place()` took 510 ms. AFTER_COMMIT listeners run on the request thread, so the HTTP caller waits for the downstream.
- **Fix:** `@Async("eventExecutor")` on a bounded pool (2-4 threads, queue 1000, CallerRuns when full).
- **After:** `place()` took 3 ms; the event is still delivered shortly afterwards.
- **Evidence:** `SlowDownstreamTest`; run with `-Doims.events.async=false` (before) and without (after). Output in `drill-before.txt` and `drill-after.txt`.
- **Remaining risk:** queued events are lost on a hard crash (shutdown waits up to 10 s to drain). Under sustained overload CallerRuns brings latency back, which is deliberate back-pressure.

## B. Duplicate delivery (documented, not fixed)
- **Trigger:** 20 concurrent `place()` calls with one `Idempotency-Key`.
- **Behaviour:** `succeeded=1 failed=19 distinctOrders=1`; stock reserved once (asserted). All 19 errors were `DataIntegrityViolationException`.
- **Gap:** lookup-then-insert race. The unique constraint prevents a double order, but the losing requests get an error (409 to the client) instead of the original order.
- **Fix designed, not applied:** catch `DataIntegrityViolationException` when a key is present and return the winner's order.
- **Evidence:** `IdempotencyRaceTest`, output in `drill-b.txt`.