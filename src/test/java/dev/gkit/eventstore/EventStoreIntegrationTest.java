package dev.gkit.eventstore;

import dev.gkit.store.Store;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class EventStoreIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("gkit")
                    .withUsername("gkit")
                    .withPassword("secret");

    static Store store;
    static EventStore es;

    private static final String SCHEMA = """
        CREATE TABLE IF NOT EXISTS events (
            stream_id   TEXT        NOT NULL,
            version     BIGINT      NOT NULL,
            type        TEXT        NOT NULL,
            data        JSONB       NOT NULL,
            metadata    JSONB       NOT NULL DEFAULT '{}',
            created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            PRIMARY KEY (stream_id, version)
        );
        CREATE TABLE IF NOT EXISTS snapshots (
            stream_id   TEXT        PRIMARY KEY,
            version     BIGINT      NOT NULL,
            data        JSONB       NOT NULL,
            created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );
        """;

    @BeforeAll
    static void setup() {
        store = new Store(Store.Config.builder()
                .url(postgres.getJdbcUrl())
                .username(postgres.getUsername())
                .password(postgres.getPassword())
                .build());
        store.update(SCHEMA);
        es = new EventStore(store);
    }

    @AfterAll
    static void teardown() {
        if (store != null) store.close();
    }

    @Test
    void appendAndLoad() {
        es.append("order-1", List.of(
                new EventStore.EventData("OrderPlaced", Map.of("id", "order-1")),
                new EventStore.EventData("StockReserved", Map.of("sku", "ABC", "qty", 2))
        ), EventStore.VERSION_NEW);

        List<EventStore.Event> events = es.load("order-1", 0);
        assertEquals(2, events.size());
        assertEquals("OrderPlaced", events.get(0).type());
        assertEquals(0L, events.get(0).version());
        assertEquals(1L, events.get(1).version());
    }

    @Test
    void versionConflictOnDuplicateNew() {
        es.append("order-2", List.of(
                new EventStore.EventData("OrderPlaced", Map.of())
        ), EventStore.VERSION_NEW);

        assertThrows(EventStore.VersionConflictException.class, () ->
                es.append("order-2", List.of(
                        new EventStore.EventData("OrderPlaced", Map.of())
                ), EventStore.VERSION_NEW)
        );
    }

    @Test
    void streamNotFound() {
        assertThrows(EventStore.StreamNotFoundException.class, () ->
                es.load("nonexistent-stream", 0)
        );
    }

    @Test
    void snapshotRoundTrip() {
        es.append("order-3", List.of(
                new EventStore.EventData("OrderPlaced", Map.of())
        ), EventStore.VERSION_NEW);

        es.saveSnapshot("order-3", 0, Map.of("count", 1));

        EventStore.Snapshot snap = es.loadSnapshot("order-3");
        assertNotNull(snap);
        assertEquals(0L, snap.version());
    }

    @Test
    void currentVersionTracksAppends() {
        es.append("order-4", List.of(
                new EventStore.EventData("E1", Map.of()),
                new EventStore.EventData("E2", Map.of()),
                new EventStore.EventData("E3", Map.of())
        ), EventStore.VERSION_NEW);

        assertEquals(2L, es.currentVersion("order-4"));
    }
}
