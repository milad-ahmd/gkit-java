package dev.gkit.eventstore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Append-only event log for event sourcing.
 * Provides the Event record, EventStore interface, and an InMemoryEventStore implementation.
 */
public final class EventStore {
    private EventStore() {}

    public static final long VERSION_NEW = -1L;
    public static final long VERSION_ANY = -2L;

    /** A persisted event in the store. */
    public record Event(String streamId, long version, String type, Object data, Map<String,Object> metadata, Instant createdAt) {
        public <T> T decode(Class<T> cls, ObjectMapper mapper) {
            return mapper.convertValue(data, cls);
        }
    }

    /** Input type for append(). */
    public record EventData(String type, Object data, Map<String,Object> metadata) {
        public EventData(String type, Object data) { this(type, data, Map.of()); }
    }

    /** Thrown on optimistic concurrency violation. */
    public static class VersionConflictException extends RuntimeException {
        public VersionConflictException(String msg) { super(msg); }
    }

    /** The event store contract. */
    public interface Store {
        void append(String streamId, List<EventData> events, long expectedVersion);
        List<Event> load(String streamId, long fromVersion);
        long currentVersion(String streamId);
        default List<Event> load(String streamId) { return load(streamId, 0); }
    }

    /** In-memory implementation for testing and prototyping. */
    public static class InMemoryStore implements Store {
        private final Map<String, CopyOnWriteArrayList<Event>> streams = new ConcurrentHashMap<>();

        @Override public synchronized void append(String streamId, List<EventData> events, long expectedVersion) {
            CopyOnWriteArrayList<Event> stream = streams.computeIfAbsent(streamId, k -> new CopyOnWriteArrayList<>());
            long current = stream.isEmpty() ? -1L : stream.get(stream.size()-1).version();
            if (expectedVersion == VERSION_NEW && current >= 0)
                throw new VersionConflictException("Stream already exists: " + streamId);
            if (expectedVersion >= 0 && current != expectedVersion)
                throw new VersionConflictException("Expected " + expectedVersion + " but was " + current);
            long next = current + 1;
            for (EventData ed : events) {
                stream.add(new Event(streamId, next++, ed.type(), ed.data(),
                        ed.metadata() != null ? ed.metadata() : Map.of(), Instant.now()));
            }
        }

        @Override public List<Event> load(String streamId, long fromVersion) {
            List<Event> stream = streams.getOrDefault(streamId, new CopyOnWriteArrayList<>());
            return stream.stream().filter(e -> e.version() >= fromVersion).collect(Collectors.toList());
        }

        @Override public long currentVersion(String streamId) {
            List<Event> stream = streams.getOrDefault(streamId, new CopyOnWriteArrayList<>());
            return stream.isEmpty() ? -1L : stream.get(stream.size()-1).version();
        }
    }
}
