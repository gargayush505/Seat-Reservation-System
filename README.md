# Seat Reservation at Scale

Java 17 + Spring Boot + Oracle + JDBC/Flyway implementation of the Paytm Money take-home exercise.

## Design

- **No double sell:** each seat is confirmed with an atomic Oracle update:
  `UPDATE seats SET status='CONFIRMED' ... WHERE status='AVAILABLE'`.
  If the update affects zero rows, the seat is already taken and the whole transaction rolls back.
- **Multi-seat requests:** all requested seat numbers are normalized and sorted before updates, preventing lock-order deadlocks. The request is **all-or-nothing**.
- **Per-user limit:** `user_show_bookings` is updated through an Oracle `MERGE` with `booked_count + requested <= per_user_limit` as the guard. This is atomic under concurrent requests for the same user/show.
- **Idempotency:** `(show_id, user_id, idempotency_key)` is a unique database constraint. The request's canonical sorted seat list is SHA-256 hashed. A replay returns the original reservation; a same-key/different-seat-list request returns `409`.
- **Release:** explicit owner-only cancellation releases confirmed seats in the same transaction.
- **Money:** integer paise (`long` in Java, `NUMBER(19,0)` in Oracle). No floating point.
- **Auth:** `Authorization: Bearer <opaque-user-token>` is the user identity. No `user_id` is accepted in reservation/cancel bodies. The admin create endpoint requires the configured admin token. In production, replace the take-home opaque-token adapter with JWT/OIDC signature validation.
- **Observability:** Actuator liveness/readiness, Prometheus metrics, request IDs, and structured log fields.

## API

### Create show

`POST /shows`

Header:
`Authorization: Bearer admin-local`

```json
{
  "name": "friday-night",
  "seats": ["A1", "A2", "A3"],
  "price_paise": 25000,
  "per_user_limit": 4
}
```

`per_user_limit` is optional and defaults to 4.

### Reserve

`POST /shows/{id}/reserve`

Header:
`Authorization: Bearer user-123`

```json
{
  "seats": ["A1", "A2"],
  "idempotency_key": "order-123"
}
```

- Initial success: `201`
- Same key + same seat list: `200` with the original reservation
- Same key + different seat list: `409`
- Seat race: `409` with `code=seat-taken`
- User limit: `409` with `code=per-user-limit`

**Partial request policy: all-or-nothing.** If any requested seat cannot be confirmed, no requested seat is confirmed.

### Cancel

`POST /reservations/{reservationId}/cancel`

Header:
`Authorization: Bearer user-123`

Only the owner can cancel. Cancellation is transactional and releases all seats belonging to that reservation.

### Show state

`GET /shows/{id}`

Returns each seat as `available`, `held`, or `confirmed`, plus counts. This implementation does not use temporary holds, so `held_seats` is always zero.

### Health and metrics

- `GET /actuator/health/liveness`
- `GET /actuator/health/readiness` — includes the database health check and fails when DB is unavailable.
- `GET /actuator/prometheus`

Important metrics include:
- `reservations_confirmed_total`
- `reservations_declined_total{reason="seat-taken"}`
- `reservations_declined_total{reason="per-user-limit"}`
- `reservations_declined_total{reason="idempotent-replay"}`
- `seats_available`

Every response receives `X-Request-Id`. Logs include the same ID through MDC.

## Run locally

### Option A: Docker Compose

Requirements: Docker.

```bash
mvn clean package -DskipTests
docker compose up --build
```

The Oracle Free container exposes port `1522` on localhost. Flyway creates the schema automatically.

### Option B: Existing Oracle Free database

Set:

```text
DB_URL=jdbc:oracle:thin:@localhost:1522/FREEPDB1
DB_USERNAME=SEAT_APP
DB_PASSWORD=your-password
ADMIN_TOKEN=admin-local
```

Then:

```bash
mvn spring-boot:run
```

## Example

```bash
curl -X POST http://localhost:8080/shows   -H 'Authorization: Bearer admin-local'   -H 'Content-Type: application/json'   -d '{"name":"friday-night","seats":["A1","A2","A3"],"price_paise":25000}'
```

Then:

```bash
curl -X POST http://localhost:8080/shows/1/reserve   -H 'Authorization: Bearer user-1'   -H 'Content-Type: application/json'   -d '{"seats":["A1"],"idempotency_key":"user-1-order-1"}'
```

## Concurrency burst

```bash
./burst.sh http://localhost:8080 2000
```

The script:
1. creates a fresh show;
2. sends a hot-seat storm where many users target `A1`;
3. concurrently retries the same idempotency key;
4. fires concurrent requests from one user to exercise the limit;
5. prints HTTP outcome distributions and the final reconciliation invariant.

For the interview's larger burst, run:

```bash
./burst.sh https://YOUR-LIVE-URL 20000
```

The script uses only Python's standard library.

## Deployment

`render.yaml` is included for a Docker deployment. Render/Railway/Fly need an externally reachable Oracle database; provide `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `ADMIN_TOKEN` as secrets. The application is intentionally stateless, so multiple instances can safely share the same Oracle database.

## Clean checkout

```bash
mvn clean test
mvn clean package
```

Do not commit `target/` or IDE metadata.
