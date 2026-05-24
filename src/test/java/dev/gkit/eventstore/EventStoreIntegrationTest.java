package dev.gkit.eventstore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EventStoreIntegrationTest {

    EventStore.Store es;

    @BeforeEach
    void setup() {
        es = new EventStore.InMemoryStore();
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
    void loadUnknownStreamReturnsEmpty() {
        List<EventStore.Event> events = es.load("nonexistent-stream", 0);
        assertTrue(events.isEmpty());
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
