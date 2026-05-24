package dev.gkit.outbox;

import dev.gkit.store.Store;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class OutboxIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("gkit")
                    .withUsername("gkit")
                    .withPassword("secret");

    static Store store;

    private static final String SCHEMA = """
        CREATE TABLE IF NOT EXISTS outbox_events (
            id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
            topic        TEXT        NOT NULL,
            payload      JSONB       NOT NULL,
            created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            published_at TIMESTAMPTZ
        );
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
    }

    @AfterAll
    static void teardown() {
        store = null;
    }

    @Test
    void storeAndRelay() throws InterruptedException {
        List<String> received = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);

        store.withTx(jdbc -> {
            Outbox.store(jdbc, "orders.placed", Map.of("id", "1"));
            Outbox.store(jdbc, "orders.placed", Map.of("id", "2"));
            return null;
        });

        Outbox.Relay relay = new Outbox.Relay(store.jdbc(), (topic, payload) -> {
            received.add(topic);
            latch.countDown();
        }, Duration.ofMillis(100), 10);

        relay.start();
        boolean done = latch.await(10, TimeUnit.SECONDS);
        relay.stop();

        assertTrue(done, "Both events should be relayed within 10s");
        assertEquals(2, received.size());
        assertTrue(received.stream().allMatch("orders.placed"::equals));
    }

    @Test
    void rollbackPreventsPublish() throws InterruptedException {
        List<String> received = new ArrayList<>();

        assertThrows(RuntimeException.class, () -> store.withTx(jdbc -> {
            Outbox.store(jdbc, "should.not.publish", Map.of("id", "x"));
            throw new RuntimeException("rollback");
        }));

        Outbox.Relay relay = new Outbox.Relay(store.jdbc(), (topic, payload) -> received.add(topic),
                Duration.ofMillis(50), 100);

        relay.start();
        Thread.sleep(300);
        relay.stop();

        long count = received.stream().filter("should.not.publish"::equals).count();
        assertEquals(0, count, "Rolled-back events must not be published");
    }
}
