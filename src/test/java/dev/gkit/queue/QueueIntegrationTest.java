package dev.gkit.queue;

import dev.gkit.store.Store;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class QueueIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("gkit")
                    .withUsername("gkit")
                    .withPassword("secret");

    static Store store;
    static Queue queue;

    private static final String SCHEMA = """
        CREATE TABLE IF NOT EXISTS jobs (
            id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
            type          TEXT        NOT NULL,
            payload       JSONB       NOT NULL,
            status        TEXT        NOT NULL DEFAULT 'pending',
            attempts      INT         NOT NULL DEFAULT 0,
            max_attempts  INT         NOT NULL DEFAULT 3,
            run_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            last_error    TEXT,
            created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );
        CREATE INDEX IF NOT EXISTS jobs_pending ON jobs (run_at) WHERE status = 'pending';
        """;

    @BeforeAll
    static void setup() {
        store = Store.open(Store.Config.builder()
                .host(postgres.getHost())
                .port(postgres.getMappedPort(5432))
                .database(postgres.getDatabaseName())
                .user(postgres.getUsername())
                .password(postgres.getPassword())
                .build());
        store.update(SCHEMA);
        queue = new Queue(store.jdbc(), Duration.ofMillis(100));
    }

    @AfterAll
    static void teardown() {
        if (queue != null) queue.stop();
        store = null;
    }

    @Test
    void enqueueAndProcess() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger processed = new AtomicInteger();

        queue.register("email", payload -> {
            processed.incrementAndGet();
            latch.countDown();
        });

        queue.enqueue("email", java.util.Map.of("to", "test@example.com"));
        queue.start(2);

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Job should be processed within 10s");
        assertEquals(1, processed.get());
    }

    @Test
    void retryOnFailure() throws InterruptedException {
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(1);

        queue.register("flaky", payload -> {
            int n = attempts.incrementAndGet();
            if (n < 2) throw new RuntimeException("transient");
            latch.countDown();
        });

        queue.enqueue("flaky", java.util.Map.of());
        queue.start(1);

        assertTrue(latch.await(30, TimeUnit.SECONDS), "Job should succeed after retry");
        assertTrue(attempts.get() >= 2);
    }
}
