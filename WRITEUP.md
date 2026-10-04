# WRITEUP — Seat Reservation at Scale

## Atomic decision

The source of truth is Oracle.

For every requested seat, the transaction executes:

```sql
UPDATE seats
SET status = 'CONFIRMED', reservation_id = :reservationId
WHERE show_id = :showId
  AND seat_number = :seatNumber
  AND status = 'AVAILABLE';
```

The affected-row count is the decision. Exactly one transaction can transition a particular `(show_id, seat_number)` from `AVAILABLE` to `CONFIRMED`. Concurrent transactions targeting the same seat wait on Oracle's row lock and then observe that the predicate is no longer true. Therefore one request wins and the rest receive a domain `409 seat-taken`; they are not treated as server errors.

There is no application-level read-then-write decision.

For multiple seats, the request is **all-or-nothing**. Seat names are sorted before updates. This provides deterministic lock ordering across requests and avoids the classic A+B versus B+A deadlock pattern. If any update affects zero rows, the transaction throws a domain exception and Spring rolls back all earlier seat updates and the reservation record.

## Per-user limit

A separate `user_show_bookings(show_id,user_id,booked_count)` row is the concurrency gate for a user's seat count.

The reservation transaction uses an Oracle `MERGE`:

```sql
WHEN MATCHED THEN UPDATE
SET booked_count = booked_count + :requested
WHERE booked_count + :requested <= :limit
```

The row is inserted with the requested count for a new user/show pair. Concurrent reservations from the same user therefore serialize on this row and cannot both pass a stale count check. If the guarded update affects zero rows, the request is declined with `409 per-user-limit`.

If a later seat decision fails, the transaction rolls back the counter change too.

## Idempotency

The database has a unique constraint on:

`(show_id, user_id, idempotency_key)`

The normalized/sorted requested seat list is stored as a SHA-256 `request_hash`.

The reservation insert uses Oracle's `IGNORE_ROW_ON_DUPKEY_INDEX` hint. Under a concurrent retry, the unique key is the database-level exactly-once boundary: one request creates the reservation and the other retrieves the committed row after the unique-key conflict is resolved.

A retry with the same key and identical body returns the original reservation and does not touch seat state or the user counter.

A retry with the same key but a different seat list compares hashes and returns `409 idempotency-mismatch`.

## Holds and expiry

This implementation chooses the assignment's explicit cancellation model instead of temporary holds.

`POST /reservations/{id}/cancel` is allowed only for the reservation owner. Reservation status and seat release are changed transactionally. The user booking counter is decremented in the same transaction.

A cancelled reservation is not reactivated by a later retry of its idempotency key. The same key continues to identify the original cancelled reservation; a new reservation must use a new idempotency key.

## Consistency vs availability

Oracle is the system of record. If the database is unavailable, readiness fails and reservation requests cannot be safely accepted. The service intentionally chooses correctness over availability because accepting a reservation without the authoritative seat transaction could violate the no-double-sell invariant.

There is no cache in the correctness path and no eventually-consistent seat state.

## Reconciliation invariant

`GET /shows/{id}` derives seat counts directly from the `seats` table. The API verifies:

`available + held + confirmed == total_seats`

The implementation has no temporary hold state, so `held` is zero.

Because reservation and seat changes are committed in one database transaction, readers see either the previous committed state or the next committed state, not a partially committed reservation.

## Observability

### Metrics

- `reservations_confirmed_total`
- `reservations_declined_total{reason=seat-taken}`
- `reservations_declined_total{reason=per-user-limit}`
- `reservations_declined_total{reason=idempotent-replay}`
- `seats_available`

The availability gauge queries the authoritative database state rather than maintaining a second in-memory counter.

### Logs

Every request receives an `X-Request-Id`; the value is placed in MDC and included in application log context. This makes a single failed reservation traceable across the HTTP request and service layer.

### 2am alerts

I would page on:
- readiness failures / database connectivity failures;
- sustained HTTP 5xx above a small threshold;
- reservation latency p95/p99 increasing sharply;
- database connection-pool exhaustion;
- reconciliation invariant violations;
- an unexpected increase in deadlocks or transaction rollbacks.

I would not page simply because `seat-taken` increases during on-sale: a high decline rate is expected during a hot-seat storm.

## AI usage

AI tools were used as an engineering assistant for:
- reviewing the starter project;
- identifying missing requirements;
- proposing Oracle transaction/idempotency patterns;
- generating initial API/repository/test scaffolding;
- reviewing edge cases and deployment artifacts.

The final correctness mechanism was deliberately kept database-centric and reviewed against Oracle transaction semantics. The developer should be able to explain why the conditional seat update, unique idempotency constraint, deterministic seat ordering, and per-user `MERGE` are race-safe.

## What I would do next

For production I would:
1. replace the take-home bearer-token identity adapter with signed JWT/OIDC validation;
2. add rate limiting and request-size limits;
3. add distributed tracing and database lock/latency dashboards;
4. add retry policies only for safe transient infrastructure errors, never for domain conflicts;
5. add an outbox/event stream if downstream payment/ticket issuance is required;
6. add load tests against an Oracle environment with realistic pool sizes and latency;
7. add automated reconciliation jobs and invariant metrics per show;
8. consider partitioning/index tuning for very large seat inventories.

## Known scope choice

The assignment asks for a public deployment. The repository includes `Dockerfile` and `render.yaml`, but the public deployment requires an Oracle database endpoint and credentials supplied by the deployment environment. Those secrets are intentionally not committed.
