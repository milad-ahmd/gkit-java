# gkit-java

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Maven Central](https://img.shields.io/badge/Maven%20Central-1.0.0-blue.svg)](#)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**gkit-java** is a production-grade Java 21 toolkit for building reliable, observable microservices.
It is a faithful port of [gkit](https://github.com/miladhzz/gkit) (Go) to idiomatic Java with Spring Boot integration.

Each package is independently usable and designed for composability.

---

## Packages

| Package | Class | Description |
|---------|-------|-------------|
| [`retry`](#retry) | `Retry`, `Backoff` | Generic retry with fixed, exponential, and jittered backoff |
| [`pool`](#pool) | `WorkerPool` | Bounded worker pool with backpressure and virtual threads |
| [`cache`](#cache) | `LruCache` | Generic LRU cache with optional TTL |
| [`async`](#async) | `Future`, `Semaphore`, `Stream` | Concurrency primitives: futures, semaphores, reactive streams |
| [`circuitbreaker`](#circuitbreaker) | `CircuitBreaker` | Closed / Open / HalfOpen state machine with configurable thresholds |
| [`ratelimit`](#ratelimit) | `RateLimiter`, `KeyedRateLimiter` | Token-bucket rate limiter with per-key eviction |
| [`pubsub`](#pubsub) | `PubSub.Bus` | Typed in-process publish/subscribe event bus |
| [`graceful`](#graceful) | `GracefulShutdown` | LIFO shutdown coordinator with timeout and JVM hook |
| [`health`](#health) | `Health.Group` | Concurrent health checks with readiness/liveness reports |
| [`metrics`](#metrics) | `Metrics.Registry` | Micrometer/Prometheus helpers with typed constructors |
| [`middleware`](#middleware) | `Middleware.*` | Servlet filters: request ID, logging, recovery, timeout |
| [`auth`](#auth) | `Auth` | JWT issuance and validation with RBAC helpers |
| [`lock`](#lock) | `DistributedLock` | Redis-backed distributed lock with SET NX + Lua release |
| [`rediscache`](#rediscache) | `RedisCache` | Generic Redis cache with JSON serialization and TTL |
| [`feature`](#feature) | `Feature.InMemoryStore` | Feature flags: global, percentage rollout, allow-list |
| [`eventstore`](#eventstore) | `EventStore` | Append-only in-memory event store with version tracking |
| [`outbox`](#outbox) | `Outbox`, `Outbox.Relay` | Transactional outbox pattern with PostgreSQL backend |
| [`pipeline`](#pipeline) | `Pipeline` | Concurrent fan-out processing and sequential stage chains |
| [`saga`](#saga) | `Saga` | Distributed saga with automatic LIFO compensation |
| [`queue`](#queue) | `Queue` | Postgres-backed durable job queue with retry + dead-letter |
| [`sched`](#sched) | `Scheduler` | Job scheduler with periodic and one-shot execution |
| [`store`](#store) | `Store` | PostgreSQL wrapper with transaction helpers and connection pool |
| [`validation`](#validation) | `Validation` | Fluent struct validation with composable rules |
| [`otel`](#otel) | `Tracer` | OpenTelemetry setup with OTLP export and span helpers |
| [`rpc`](#rpc) | `RpcServer`, `RpcClient` | gRPC server/client builder with interceptor chains |

---

## Requirements

- Java 21+
- Spring Boot 3.2+ (optional, most packages work standalone)
- Maven 3.9+

---

## Installation

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>dev.gkit</groupId>
    <artifactId>gkit-java</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## Usage

### retry

```java
import dev.gkit.retry.*;

RetryOptions opts = RetryOptions.builder()
    .maxAttempts(5)
    .backoff(Backoff.exponential(Duration.ofMillis(100), 2.0, Duration.ofSeconds(10)))
    .onRetry((attempt, err) -> log.warn("Attempt {}: {}", attempt, err.getMessage()))
    .build();

String result = Retry.execute(opts, () -> externalService.call());
```

### pool

```java
import dev.gkit.pool.*;

WorkerPool pool = WorkerPool.builder()
    .size(16)
    .queueCapacity(1000)
    .build();

Future<String> result = pool.submit(() -> processItem(item));
pool.drain(); // wait for all tasks
```

### cache

```java
import dev.gkit.cache.*;

LruCache<String, Product> cache = new LruCache<>(1000);
cache.put("prod-1", product, Duration.ofMinutes(10));
Optional<Product> p = cache.get("prod-1");
```

### async

```java
import dev.gkit.async.*;

// Parallel futures
CompletableFuture<String> f = Future.of(() -> fetchFromApi());
Future.all(List.of(f1, f2, f3)).get(); // wait for all

// Semaphore
Semaphore sem = new Semaphore(10);
sem.acquire();
try { doWork(); } finally { sem.release(); }

// Stream processing
Stream.fromList(urls)
    .map(url -> downloadAndResize(url))
    .filter(img -> img.size() < 1_000_000)
    .forEach(img -> store(img));
```

### circuitbreaker

```java
import dev.gkit.circuitbreaker.*;

CircuitBreaker cb = CircuitBreaker.builder()
    .failureThreshold(5)
    .successThreshold(2)
    .openTimeout(Duration.ofSeconds(30))
    .onStateChange((from, to) -> log.info("CB {} → {}", from, to))
    .build();

String result = cb.execute(ctx -> externalService.call());
```

### ratelimit

```java
import dev.gkit.ratelimit.*;

// Global limiter: 100 req/s, burst of 20
RateLimiter limiter = RateLimiter.create(100.0, 20);
if (!limiter.allow()) throw new RateLimitExceededException();

// Per-IP limiter
KeyedRateLimiter<String> keyed = KeyedRateLimiter.create(10.0, 5);
if (!keyed.allow(request.getRemoteAddr())) {
    response.setStatus(429);
    return;
}
```

### pubsub

```java
import dev.gkit.pubsub.*;

PubSub.Bus bus = new PubSub.Bus();

// Subscribe
Runnable unsub = PubSub.subscribe(bus, "orders.placed", (OrderPlaced event) -> {
    processOrder(event);
});

// Publish
PubSub.publish(bus, "orders.placed", new OrderPlaced("order-123"));

// Unsubscribe
unsub.run();
```

### graceful

```java
import dev.gkit.graceful.*;

GracefulShutdown g = GracefulShutdown.builder()
    .timeout(Duration.ofSeconds(30))
    .build()
    .installShutdownHook();

g.register("http-server", () -> server.stop());
g.register("worker-pool", () -> pool.drain());
// On SIGTERM: hooks run in LIFO order within 30s
```

### health

```java
import dev.gkit.health.*;

Health.Group group = Health.Group.builder()
    .checkTimeout(Duration.ofSeconds(5))
    .build();

group.register("database", () -> db.ping());
group.register("redis",    () -> redis.ping());

Health.Report report = group.check();
// report.healthy() → true/false
// report.checks()  → per-check status list
```

### auth

```java
import dev.gkit.auth.*;

byte[] secret = System.getenv("JWT_SECRET").getBytes();

// Issue token
String token = Auth.issueToken(
    new Auth.Claims("user-123", List.of("admin", "user")),
    secret,
    Duration.ofHours(24)
);

// Validate token
Auth.Claims claims = Auth.validateToken(token, secret);
claims.hasRole("admin"); // true
```

### lock

```java
import dev.gkit.lock.*;

DistributedLock locker = new DistributedLock(redisTemplate);

// Auto-release with lambda
locker.withLock("billing:invoice:123", Duration.ofSeconds(30), () -> {
    processInvoice(invoiceId);
});

// Manual lifecycle
String token = locker.acquire("report:monthly", Duration.ofMinutes(5));
try {
    generateReport();
} finally {
    locker.release("report:monthly", token);
}
```

### saga

```java
import dev.gkit.saga.*;

Saga saga = Saga.named("place-order",
    Saga.step("reserve-inventory")
        .execute(() -> inventory.reserve(item))
        .compensate(() -> inventory.release(item))
        .build(),
    Saga.step("charge-payment")
        .execute(() -> payments.charge(amount))
        .compensate(() -> payments.refund(amount))
        .build(),
    Saga.step("send-confirmation")
        .execute(() -> email.send(order))
        .build()
);

try {
    saga.run();
} catch (Saga.SagaException e) {
    log.error("Saga failed at step '{}': {}", e.getFailedStep(), e.getCause().getMessage());
}
```

### validation

```java
import dev.gkit.validation.*;

Validation.validate()
    .field("email",    email,    Validation.required(), Validation.email())
    .field("quantity", qty,      Validation.min(1), Validation.max(1000))
    .field("status",   status,   Validation.oneOf("pending", "active", "cancelled"))
    .validate(); // throws ValidationException if invalid
```

### sched

```java
import dev.gkit.sched.*;

Scheduler sched = Scheduler.builder()
    .workers(4)
    .onError((job, err) -> log.error("Job {} failed: {}", job.name(), err.getMessage()))
    .build();

sched.every(Duration.ofMinutes(1), "cleanup",  () -> db.deleteOldRecords());
sched.every(Duration.ofHours(6),   "report",   () -> reports.generate());
sched.after(Duration.ofSeconds(5), "warmup",   () -> cache.warm());
sched.start();
```

### store

```java
import dev.gkit.store.*;

Store store = Store.open(Store.Config.builder()
    .host("localhost").port(5432)
    .database("mydb").user("app").password("secret")
    .build());

// Query
List<Map<String, Object>> rows = store.query("SELECT * FROM orders WHERE status = ?", "pending");

// Transaction
store.withTx(jdbc -> {
    jdbc.update("INSERT INTO orders (id, status) VALUES (?, ?)", id, "pending");
    jdbc.update("INSERT INTO events (order_id, type) VALUES (?, ?)", id, "order.created");
    return null;
});
```

---

## Architecture

Each package follows these conventions:

- **No inter-package dependencies** (except where explicitly noted)
- **Thread-safe** — all public APIs are safe for concurrent use
- **Fail-fast** — misconfigurations throw exceptions at construction time, not at runtime
- **Virtual-thread friendly** — blocking APIs use `Thread.sleep` / `ReentrantLock`, compatible with Java 21 virtual threads
- **Spring Boot optional** — Spring integration is provided where useful (filters, templates) but never required

---

## Building

```bash
mvn clean package -DskipTests
mvn test
```

---

## License

MIT — see [LICENSE](LICENSE).
