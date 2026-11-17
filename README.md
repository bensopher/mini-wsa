# Mini WSA

A small security analytics pipeline: a Spring Boot service that ingests web security events, enriches them with an attack type and a threat score (including a Redis-backed repeat-offender rule), stores them in PostgreSQL + TimescaleDB, and serves statistics, samples, time series, rate-limited reads and alerting over a REST API.

**Stack:** Java 21 (virtual threads) · Spring Boot 4.1 · PostgreSQL 17 + TimescaleDB · Redis · Flyway · JdbcTemplate · Spring Security (API keys) · Testcontainers · Docker Compose.

> **Status: work in progress.** The project is built in milestones (see [Development process](#development-process)). **Available now:** authenticated event ingestion with validation, duplicate detection, enrichment (attack type and threat score, including the Redis-backed repeat-offender rule) and storage in a TimescaleDB hypertable. Sections marked *coming* are filled in as each milestone lands.

## Quick start

Requires Docker (Docker Desktop on Windows/macOS). Java 21 is only needed to build or test outside Docker.

Bash (Git Bash, macOS, Linux):

```bash
git clone https://github.com/bensopher/mini-wsa.git
cd mini-wsa
docker compose up --build -d
curl http://localhost:8080/actuator/health
curl -X POST http://localhost:8080/v1/events/ingest -H "Content-Type: application/json" -H "X-API-Key: dev-ingest-key" --data-binary @examples/single-event.json
```

PowerShell (use `curl.exe`: plain `curl` is an alias for `Invoke-WebRequest`):

```powershell
git clone https://github.com/bensopher/mini-wsa.git
cd mini-wsa
docker compose up --build -d
curl.exe http://localhost:8080/actuator/health
curl.exe -X POST http://localhost:8080/v1/events/ingest -H "Content-Type: application/json" -H "X-API-Key: dev-ingest-key" --data-binary "@examples/single-event.json"
```

The first build downloads dependencies and takes a few minutes. The app is ready when `/actuator/health` returns `"status":"UP"`.

### Run modes

| Mode | Commands |
|---|---|
| Everything in Docker | `docker compose up --build -d` |
| App from source (database in Docker) | `docker compose up -d db redis`, then `./mvnw -pl mini-wsa-app spring-boot:run` (PowerShell: `.\mvnw.cmd -pl mini-wsa-app spring-boot:run`) |
| Tests | `./mvnw verify` (PowerShell: `.\mvnw.cmd verify`); Docker must be running |

If ports 5432, 6379 or 8080 are already in use, remap them: `DB_PORT=5433 REDIS_PORT=6380 APP_PORT=8081 docker compose up -d` (bash) or `$env:DB_PORT=5433; $env:REDIS_PORT=6380; $env:APP_PORT=8081; docker compose up -d` (PowerShell). When running the app from source, set `DB_PORT` the same way.

The database credentials and API keys in `docker-compose.yml` and `application.yml` are **dev-only** defaults.

## Architecture

*Coming.*

## Storage choice

*Coming.*

## Enrichment and threat scoring

Every stored event gets an **attack type** and a **threat score** (0–100), computed once at ingest.

### Attack type

| `rule.category` | `attackType` |
|---|---|
| `INJECTION` | SQL/Command Injection |
| `XSS` | Cross-Site Scripting |
| `PROTOCOL_VIOLATION` | Protocol Anomaly |
| `DATA_LEAKAGE` | Data Exfiltration |
| `BOT` | Bot Activity |
| `DOS` | Denial of Service |
| `RATE_LIMIT` | Rate Limiting |

### Threat score

The score is the sum of independent rules, capped at 100. Each rule is its own class (`ScoreRule`), so a new rule is a new class; weights live on the enums and in `miniwsa.scoring.*`.

| Rule | Points | Configured in |
|---|---|---|
| Severity | `CRITICAL` 40, `HIGH` 30, `MEDIUM` 20, `LOW` 10 | `Severity` enum |
| Action | `DENY` +20, `ALERT` +10, `MONITOR` +0 | `Action` enum |
| Sensitive path | +15 if the path contains `/admin` or `/login`, case-insensitive (A7); `/administrator` matches, `/wp-admin` doesn't | `miniwsa.scoring.sensitive-path` |
| Repeat offender | +15 if at least 5 *other* events from the same client IP fall in the 10 minutes up to this event (A5, A6) | `miniwsa.scoring.repeat-offender` |

Example: the sample event (`CRITICAL`, `DENY`, `/api/v1/login`) scores 40 + 20 + 15 = **75**, or **90** from a repeat offender.

With these weights the maximum is 40 + 20 + 15 + 15 = **90**, so the cap of 100 never triggers today. It is still implemented and tested, because weights are configurable and rules get added.

### Repeat-offender rule

State lives in **Redis**: one sorted set per client IP (`miniwsa:ro:<ip>`), member = `eventId:timestamp`, score = event time in milliseconds. A Lua script counts, records and prunes in one atomic step.

| Property | How |
|---|---|
| **Event time (A5)** | The window is `[event.timestamp − 10m, event.timestamp]`, so out-of-order arrivals are counted by when they happened, not when they arrived. A batch is processed in event-time order, so earlier events of the same request count (A6). |
| **Scores are frozen at ingest** | A late event doesn't re-score events already stored. |
| **Atomic across instances** | Each IP's update is one Lua script call, which Redis runs atomically, so several app instances see consistent counts. This is why the state is in Redis, not in an in-memory map. |
| **Idempotent (A4)** | The member is the event's identity, so a retried event neither counts itself nor inflates the others; the same `eventId` at another time is another event. |
| **Efficient** | One script call per IP per request, all sent in a single pipeline: one round trip per request. |
| **Bounded memory** | Entries older than 1 hour before the IP's newest event are pruned (relative to event time, so historical data works), and idle IPs expire after 2 hours. |
| **Cluster-ready** | Each script call touches a single key, so it also works on Redis Cluster (a multi-key script across IPs wouldn't). |

**Trade-offs:**
- **Redis is written before Postgres.** If the database insert then fails, Redis remembers events that were never stored. A client retry is still safe (idempotent), at the cost of slightly inflated counts for events that are never retried. The production fix is a log-based pipeline: Kafka partitioned by client IP feeding a stateful stream processor.
- **Fail open.** If Redis is down or hanging, ingestion continues: events are scored without the repeat bonus, a `WARN` is logged and the `miniwsa.enrichment.repeat_offender.unavailable` metric is incremented. In security analytics, losing events is worse than under-scoring some for a while. Redis timeouts are 500 ms (Lettuce's default is 60 s), so an outage costs at most about half a second per request; an integration test pauses and stops Redis to prove it.

## API reference

### Authentication

Every endpoint except `/actuator/health` requires an `X-API-Key` header whose key has the required role. The dev keys (overridable with the environment variables shown) are:

| Key | Env variable | Roles | Can |
|---|---|---|---|
| `dev-ingest-key` | `MINIWSA_INGEST_KEY` | `INGEST` | ingest events |
| `dev-read-key` | `MINIWSA_READ_KEY` | `READ` | read stats, samples and alerts (coming) |
| `dev-admin-key` | `MINIWSA_ADMIN_KEY` | `READ`, `ADMIN` | read, define alerts (coming), `/actuator/info`, `/actuator/metrics` |

A missing or unknown key gets `401`; a key without the required role gets `403`.

### Errors

Every error is an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem (`Content-Type: application/problem+json`) with a stable `type` URI. Validation errors list every problem in an `errors` array:

| Field | Meaning |
|---|---|
| `index` | Position in the batch (omitted for a single-object request) |
| `field` | Dotted path, e.g. `rule.severity` |
| `message` | What is wrong; enum errors list the allowed values |
| `rejectedValue` | The value received (omitted when absent) |

| Status | `type` suffix | When |
|---|---|---|
| 400 | `validation` | One or more fields are invalid |
| 400 | `malformed-json` | The body is not JSON, or exceeds the nesting (20) or string-length (64 KB) limits |
| 401 | `unauthorized` | Missing or unknown API key |
| 403 | `forbidden` | The key lacks the required role |
| 413 | `payload-too-large` | Body over 5 MB, or more than 1000 events |
| 415 | (Spring default) | `Content-Type` is not `application/json` |
| 500 | `internal` | Unexpected error; the body only carries an `errorId` that matches the server log |

### `POST /v1/events/ingest`

Role: `INGEST`. Body: one event object, or a JSON array of up to 1000 events.

| Field | Required | Rule |
|---|---|---|
| `eventId` | yes | non-blank, ≤ 128 characters |
| `timestamp` | yes | ISO-8601 with an offset (`Z` or `+02:00`); normalized to UTC; at most 5 minutes in the future |
| `configId` | yes | positive integer (a JSON number: `"123"` and `1.5` are rejected) |
| `policyId` | no | ≤ 128 |
| `clientIp` | yes | IPv4 or IPv6 literal (no hostnames, no zone IDs); stored in canonical form |
| `hostname` | no | ≤ 253 |
| `path` | yes | starts with `/`, ≤ 2048 |
| `method` | yes | any HTTP token, ≤ 16 (attack traffic uses non-standard methods) |
| `statusCode` | no | 100–599 |
| `userAgent` | no | ≤ 1024 |
| `rule.id` | yes | non-blank, ≤ 32 |
| `rule.name`, `rule.message` | no | ≤ 128 / ≤ 512 |
| `rule.severity` | yes | `CRITICAL`, `HIGH`, `MEDIUM`, `LOW` (case-sensitive) |
| `rule.category` | yes | `INJECTION`, `XSS`, `PROTOCOL_VIOLATION`, `DATA_LEAKAGE`, `BOT`, `DOS`, `RATE_LIMIT` |
| `action` | yes | `DENY`, `ALERT`, `MONITOR` |
| `geoLocation.country` | no | ISO 3166-1 alpha-2 (`US`) |
| `geoLocation.city` | no | ≤ 128 |
| `requestSize`, `responseSize` | no | ≥ 0 |

Unknown fields are ignored, so producers can add fields without breaking ingestion.

**Behavior**

- **All-or-nothing (A1).** If any event is invalid, the response is `400` listing every error, and nothing is stored. Each element is converted separately, so a type error is reported with its index. A type error stops checks on that element, so its other fields are validated once the type is fixed.
- **Duplicates (A4).** An event is identified by `(eventId, timestamp)`. Sending it again stores nothing and reports it as a duplicate; a request made only of duplicates still returns `201`, so retries are safe. The same `eventId` with a different timestamp is a different event.
- **Enrichment.** Each event in the response carries its `attackType` and `threatScore` (see [Enrichment and threat scoring](#enrichment-and-threat-scoring)). For a duplicate they are computed for this request and not stored; the stored row keeps the score from its first ingestion.
- **Durable acknowledgment.** `201` means the events are committed to the database. Ingestion is synchronous on purpose: there is no in-memory queue that could lose acknowledged events.

Single event:

```bash
curl -X POST http://localhost:8080/v1/events/ingest \
  -H "Content-Type: application/json" -H "X-API-Key: dev-ingest-key" \
  --data-binary @examples/single-event.json
```

```powershell
curl.exe -X POST http://localhost:8080/v1/events/ingest -H "Content-Type: application/json" -H "X-API-Key: dev-ingest-key" --data-binary "@examples/single-event.json"
```

```json
{
  "received": 1, "stored": 1, "duplicates": 0,
  "receivedAt": "2026-11-17T17:56:17.558Z",
  "events": [
    { "eventId": "evt-00132", "attackType": "SQL/Command Injection", "threatScore": 75, "duplicate": false }
  ]
}
```

Batch (`examples/batch-events.json`: its third event repeats the single event above, so it is reported as a duplicate):

```bash
curl -X POST http://localhost:8080/v1/events/ingest \
  -H "Content-Type: application/json" -H "X-API-Key: dev-ingest-key" \
  --data-binary @examples/batch-events.json
```

```powershell
curl.exe -X POST http://localhost:8080/v1/events/ingest -H "Content-Type: application/json" -H "X-API-Key: dev-ingest-key" --data-binary "@examples/batch-events.json"
```

```json
{
  "received": 3, "stored": 2, "duplicates": 1,
  "receivedAt": "2026-11-17T17:56:17.704Z",
  "events": [
    { "eventId": "evt-00133", "attackType": "Bot Activity", "threatScore": 20, "duplicate": false },
    { "eventId": "evt-00134", "attackType": "Cross-Site Scripting", "threatScore": 40, "duplicate": false },
    { "eventId": "evt-00132", "attackType": "SQL/Command Injection", "threatScore": 75, "duplicate": true }
  ]
}
```

Invalid batch (`examples/invalid-batch.json`), `400`:

```bash
curl -X POST http://localhost:8080/v1/events/ingest \
  -H "Content-Type: application/json" -H "X-API-Key: dev-ingest-key" \
  --data-binary @examples/invalid-batch.json
```

```powershell
curl.exe -X POST http://localhost:8080/v1/events/ingest -H "Content-Type: application/json" -H "X-API-Key: dev-ingest-key" --data-binary "@examples/invalid-batch.json"
```

```json
{
  "type": "https://github.com/bensopher/mini-wsa/errors/validation",
  "title": "Validation failed",
  "status": 400,
  "detail": "2 of 3 events failed validation; nothing was stored",
  "instance": "/v1/events/ingest",
  "errors": [
    { "index": 1, "field": "rule.severity", "message": "must be one of [CRITICAL, HIGH, MEDIUM, LOW]", "rejectedValue": "SEVERE" },
    { "index": 2, "field": "configId", "message": "must be an integer", "rejectedValue": "abc" }
  ]
}
```

Swagger UI is coming at `/swagger-ui.html`.

## Data generator

*Coming.*

## Running tests

`./mvnw verify` (PowerShell: `.\mvnw.cmd verify`) runs unit tests (Surefire, `*Test`) and integration tests (Failsafe, `*IT`). Integration tests start the full application on a random port against TimescaleDB and Redis containers (Testcontainers), so Docker must be running. If containers can't be found, restart Docker Desktop. Integration tests log every HTTP exchange (method, path, status, bodies; never headers) under the name of the test that made it.

| Area | Covered by |
|---|---|
| Field validation (every required field, enums, timestamps, IPv4/IPv6, lengths, ranges) | `EventPayloadValidationTest` |
| Strict JSON typing, nesting and string limits | `JsonConfigTest`, `IngestIT` |
| Body-size limit (declared and chunked bodies) | `RequestBodyLimitFilterTest`, `RequestBodyLimitIT` |
| Problem responses; no leaking of exception text | `GlobalExceptionHandlerTest` |
| API keys, roles, 401/403, public health, error dispatch | `ApiKeyAuthenticatorTest`, `SecurityIT` |
| Multi-row insert, duplicate detection, chunking | `JdbcEventWriteRepositoryIT` |
| The same event from 8 concurrent requests is stored exactly once | `ConcurrentIngestIT` |
| Attack type for every category; every score rule and its boundaries; the sample event's 75 and 90; the cap | `AttackTypeClassifierTest`, `*ScoreRuleTest`, `ThreatScoreCalculatorTest` |
| Event-time ordering, request order of results, fail-open | `EnrichmentServiceTest` |
| Repeat-offender state in Redis: counts 0–5, alignment, out-of-order arrival, retries, same id at two times, window boundaries, isolation per IP, TTL, pruning, script cache loss | `RedisRepeatOffenderTrackerIT` |
| Enrichment end to end: 6th and 7th events get +15, across requests, out-of-order batches, no inflation from retries | `EnrichmentIT` |
| Redis paused or stopped: ingestion still succeeds within 2 s | `RedisUnavailableIT` |
| Ingest API end to end: single, batch, all-or-nothing, indexed errors, idempotency, future timestamps, normalization | `IngestIT` |
| Hypertable migration | `MigrationIT` |

## Design decisions and assumptions

These assumptions were sent to the interviewer for confirmation. Code that depends on one is tagged with its ID (e.g. `// A3`).

| ID | Question | Assumption implemented |
|---|---|---|
| A1 | Batch with some invalid events | **All-or-nothing**: any invalid event → `400` with per-index errors, nothing stored |
| A2 | Required vs optional fields | Required: `eventId, timestamp, configId, clientIp, path, method, rule.id, rule.severity, rule.category, action`; everything else optional |
| A3 | Timestamp format | ISO-8601 with an offset, normalized to UTC; more than 5 minutes in the future is rejected (configurable) |
| A4 | Duplicate `eventId` | Idempotent: the same `(eventId, timestamp)` is stored once, reported as a duplicate, and doesn't count twice toward the repeat-offender rule |
| A5 | "Last 10 minutes" reference | Event time: the window is `[event.timestamp − 10m, event.timestamp]` |
| A6 | "More than 5 events" | The bonus applies when ≥ 5 *other* events from the same IP fall in the window (6 in total > 5); earlier events in the same batch count |
| A7 | Path rule | Case-insensitive substring match on `/admin` or `/login` |
| A8 | `from`/`to` on stats and samples | Optional; default `to = now`, `from = to − 24h`; half-open `[from, to)`; `from >= to` is rejected |
| A9 | Which time field filters use | The event `timestamp`, not `receivedAt` |
| A10 | Scale and infrastructure | Minimal runtime (app + Postgres/Timescale + Redis); the production-scale design (Kafka keyed by IP, an OLAP store) will be described under Storage choice |
| A11 | Generator language | Java, as a separate module |

Other decisions so far:

- **Enum-like columns are `TEXT` without CHECK constraints.** The application validates them, and adding a category must not require a migration.
- **Duplicate detection uses `INSERT … ON CONFLICT DO NOTHING RETURNING event_id, event_time`** (one multi-row statement per 500 events, one transaction per request). `RETURNING` names exactly the rows that were written; `batchUpdate` with pgjdbc's batch rewriting reports every row as `SUCCESS_NO_INFO` and can't tell duplicates apart.
- **The unique key is `(event_id, event_time)`** because a TimescaleDB hypertable's unique keys must include its partition column.
- **Strict JSON typing**: strings are never coerced to numbers and floats never to integers, so malformed producer output is reported instead of silently "fixed".

## Observability

*Coming:* the full metrics list. Available now: `/actuator/health` (public; shows `db` and `redis` status), and for the `ADMIN` key `/actuator/info` and `/actuator/metrics`, including `miniwsa.security.auth.failures` and `miniwsa.enrichment.repeat_offender.unavailable`. Each ingest request logs one line with counts and duration; payloads are never logged, because events are attacker-controlled and may contain sensitive data. In Docker, logs are structured JSON (ECS).

## Security

Implemented so far:

- **Stateless API-key authentication** (`X-API-Key`) with roles `INGEST`, `READ` and `ADMIN`. Ingest keys can't read and read keys can't ingest, which limits data poisoning and data exposure from a leaked key.
- Keys come only from configuration (environment variables in production). Only their SHA-256 hashes are kept in memory, and a presented key is compared against every key in constant time. Logs identify a client by its key's name, never by the key.
- 401 and 403 are problem responses, counted in the `miniwsa.security.auth.failures` metric (tag `reason` = `missing` | `invalid` | `forbidden`) and logged with the client IP only.
- **Input hardening:** a 5 MB body limit enforced before authentication (including chunked bodies), at most 1000 events per request, JSON nesting depth ≤ 20 and strings ≤ 64 KB, strict validation, IP parsing without DNS lookups.
- **No information leakage:** 500s carry only an error id; stack traces and exception messages never reach clients. The public health endpoint shows each component's status, not its details.
- CSRF protection is off because there are no cookies or browser sessions; CORS is not enabled because there are no browser clients.
- The container runs as a non-root user; image tags are pinned; Dependabot watches Maven and Docker dependencies.

*Coming:* the full threat model and production recommendations.

## Performance and concurrency

*Coming.*

## Design patterns

*Coming.*

## What I'd improve with more time

*Coming.*

## Challenges and how I solved them

*Coming.*

## Development process

- Small commits in [Conventional Commits](https://www.conventionalcommits.org/) format; every commit builds and passes `./mvnw verify`, with tests in the same commit as the code they cover.
- One annotated tag per milestone: `v0.1-ingestion`, `v0.2-enrichment`, `v0.3-stats`, `v0.4-samples`, `v0.5-generator`, then the bonuses `v0.6-timeseries`, `v0.7-rate-limiting`, `v0.8-alerting`, and `v1.0.0`.
- AI-assisted tooling (Claude Code) was used during development, as the assignment allows. All code was reviewed and is understood by the author.
