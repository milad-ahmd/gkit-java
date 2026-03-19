# Changelog

All notable changes to **gkit-java** are documented here.

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) · Versioning: [SemVer](https://semver.org/)

---

## [Unreleased]

### Added
- Integration tests with Testcontainers for `store`, `rediscache`, `lock`, `queue`, `outbox`, `eventstore`
- Community health files: `CONTRIBUTING.md`, `SECURITY.md`, `CODEOWNERS`
- GitHub issue and PR templates

---

## [1.0.0] — 2024-03-19

### Added

**Core concurrency**
- `retry` — generic retry with fixed, exponential, and jitter backoff; `StopException` escape hatch
- `pool` — bounded virtual-thread worker pool with backpressure and `drain()`
- `cache` — generic LRU cache with optional per-entry TTL
- `async` — `Future<T>`, `Semaphore`, `Stream<T>` concurrency primitives via virtual threads
- `pipeline` — concurrent fan-out pipeline with stage chaining and composition

**Reliability**
- `circuitbreaker` — Closed/Open/HalfOpen state machine with configurable thresholds
- `ratelimit` — token-bucket rate limiter with per-key variant and TTL eviction
- `graceful` — LIFO shutdown coordinator with per-hook timeout and JVM shutdown hook
- `health` — concurrent health-check group with per-checker timeout
- `saga` — saga orchestrator with LIFO compensation and structured error reporting
- `pubsub` — typed in-process publish/subscribe bus via virtual threads

**Infrastructure**
- `store` — PostgreSQL layer (JdbcTemplate + TransactionTemplate)
- `rediscache` — Redis-backed cache with Jackson JSON serialisation
- `lock` — Redis distributed lock with Lua release/renewal
- `queue` — Postgres-backed job queue with exponential backoff and dead-letter
- `outbox` — transactional outbox pattern with background relay
- `eventstore` — append-only Postgres event store with version-checked appends
- `rpc` — gRPC server and client builders with interceptors and TLS

**Cross-cutting**
- `auth` — JWT issuance and validation (JJWT) with RBAC helpers
- `metrics` — Micrometer registry with typed counter, gauge, timer, summary
- `middleware` — Servlet filters: request ID, structured logging, recovery, timeout
- `otel` — OpenTelemetry SDK setup with OTLP export
- `config` — environment-variable config loader with type coercion
- `feature` — feature flags with FNV-1a percentage rollout and env-var loading
- `sched` — fixed-rate and one-shot job scheduler
- `validation` — fluent field-level validator with built-in rules

[Unreleased]: https://github.com/miladhzz/gkit-java/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/miladhzz/gkit-java/releases/tag/v1.0.0
