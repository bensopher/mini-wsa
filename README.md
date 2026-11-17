# Mini WSA

A small security analytics pipeline: a Spring Boot service that ingests web security events, enriches them with an attack type and a threat score (including a Redis-backed repeat-offender rule), stores them in PostgreSQL + TimescaleDB, and serves statistics, samples, time series, rate-limited reads and alerting over a REST API.

**Stack:** Java 21 (virtual threads) · Spring Boot 4.1 · PostgreSQL 17 + TimescaleDB · Redis · Flyway · JdbcTemplate · Spring Security (API keys) · Testcontainers · Docker Compose.

> **Status: work in progress.** The project is built in milestones (see [Development process](#development-process)). Sections marked *coming* are filled in as each milestone lands.

## Quick start

Requires Docker (Docker Desktop on Windows/macOS) and Java 21.

Bash (Git Bash, macOS, Linux):

```bash
git clone https://github.com/bensopher/mini-wsa.git
cd mini-wsa
docker compose up -d      # TimescaleDB and Redis
./mvnw verify             # build and run the tests
```

PowerShell:

```powershell
git clone https://github.com/bensopher/mini-wsa.git
cd mini-wsa
docker compose up -d
.\mvnw.cmd verify
```

If ports 5432 or 6379 are already in use, remap them: `DB_PORT=5433 REDIS_PORT=6380 docker compose up -d` (bash) or `$env:DB_PORT=5433; $env:REDIS_PORT=6380; docker compose up -d` (PowerShell).

The compose credentials are **dev-only** defaults.

## Architecture

*Coming.*

## Storage choice

*Coming.*

## Enrichment and threat scoring

*Coming.*

## API reference

*Coming.* Swagger UI will be served at `/swagger-ui.html`.

## Data generator

*Coming.*

## Running tests

`./mvnw verify` (Windows: `.\mvnw.cmd verify`) runs unit tests (Surefire, `*Test`) and integration tests (Failsafe, `*IT`). Integration tests use Testcontainers, so Docker must be running. If containers can't be found, restart Docker Desktop.

## Design decisions and assumptions

*Coming.*

## Observability

*Coming.*

## Security

*Coming.*

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
