# Order & Inventory Management Service

A multi-warehouse order service that reserves stock without overselling, moves orders through a lifecycle, and emits events for downstream systems.

**Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, H2 (file), Spring Security (JWT resource server), JUnit 5, Maven.

## Prerequisites

- JDK 21
- Maven 3.9+

## Run

```bash
mvn spring-boot:run             # http://localhost:8080
mvn clean test                  # full suite
```

Reset data: stop the app, then `rm -rf data/`. Seed data (products SKU-001..003, warehouses WH-NORTH and WH-SOUTH) is reloaded on startup.

| URL | Purpose |
|---|---|
| `/swagger-ui.html` | Interactive API docs (use **Authorize** to paste a token) |
| `/h2-console` | DB console. JDBC URL `jdbc:h2:file:./data/oims`, user `sa`, empty password |
| `/actuator/health` | Health check |

## Try it

1. `POST /api/dev/token` with `{"subject":"alice","roles":["CUSTOMER"]}`. Roles: `CUSTOMER`, `WAREHOUSE_OPS`, `ADMIN`. **Dev only.**
2. Click **Authorize** in Swagger and paste the token without `Bearer`.
3. `POST /api/orders` with `{"lines":[{"productId":1,"quantity":3}]}`. Optional `Idempotency-Key` header.
4. Mint a `WAREHOUSE_OPS` token, then:
    - `POST /api/orders/{id}/status` with `{"target":"PAID"}`
    - `GET /api/reports/top-products`
    - `GET /api/reports/low-stock?threshold=20`
5. `POST /api/orders/{id}/cancel` releases stock; calling it twice is safe.

## Roles

| Endpoint | Allowed |
|---|---|
| `POST /api/orders` | `CUSTOMER` |
| `GET /api/orders/{id}`, `POST /api/orders/{id}/cancel` | Owner, `WAREHOUSE_OPS`, `ADMIN` |
| `POST /api/orders/{id}/status`, `/api/stock/**`, `/api/reports/**` | `WAREHOUSE_OPS`, `ADMIN` |
| `POST /api/products` | `ADMIN` |
| `GET /api/products` | Any authenticated user |

## Configuration (`application.yml`)

| Property | Default | Meaning |
|---|---|---|
| `oims.reservation.strategy` | `pessimistic` | `pessimistic` or `optimistic` locking |
| `oims.reservation.ttl-minutes` | `15` | Unpaid reservation lifetime |
| `oims.reservation.max-attempts` | `5` | Optimistic retries |
| `oims.reservation.expiry-check-ms` | `30000` | Expiry job interval |
| `oims.events.async` | `true` | Async event listeners |
| `oims.security.dev-tokens` | `true` | **Must be false outside local/demo** |
| `DB_POOL` (env) | `30` | Hikari pool size |

## Load test

App must be running.

```bash
mvn -q test-compile exec:java -Dexec.classpathScope=test \
  -Dexec.mainClass=com.acme.oims.load.LoadTest -Dexec.args="100 30 5"
```

Arguments: users, measured seconds, warm-up seconds. Add `-Dhot=1.0` to send all traffic to one product.

Optional k6 script:

```bash
k6 run loadtest/orders.js
```

Results and analysis: `DECISIONS.md` (D-021 to D-023).

## Documentation

| File | Content |
|---|---|
| `DESIGN.md` | Final design |
| `DECISIONS.md` | Decision log and technical debt |
| `DRILLS.md` | Failure drills with evidence |
| `SELF_REVIEW.md` | Three weakest spots |
| `docs/openapi.yaml` | Exported OpenAPI specification (fetching it needs a token) |