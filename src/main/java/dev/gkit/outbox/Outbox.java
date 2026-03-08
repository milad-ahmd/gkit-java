package dev.gkit.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Transactional outbox pattern for reliable event publishing.
 *
 * <p>Schema (run before use):
 * <pre>{@code
 * CREATE TABLE IF NOT EXISTS outbox_events (
 *     id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
 *     topic        TEXT        NOT NULL,
 *     payload      JSONB       NOT NULL,
 *     created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *     published_at TIMESTAMPTZ
 * );
 * }</pre>
 *
 * <pre>{@code
 * Outbox.Relay relay = new Outbox.Relay(jdbcTemplate, myPublisher);
 * relay.start();
 * // In business logic (same transaction):
 * Outbox.store(jdbcTemplate, "orders.placed", orderEvent);
 * }</pre>
 */
public final class Outbox {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Outbox() {}

    @FunctionalInterface
    public interface Publisher {
        void publish(String topic, byte[] payload) throws Exception;
    }

    /** Inserts an event into the outbox table. Call within an existing transaction. */
    public static void store(JdbcTemplate jdbc, String topic, Object payload) {
        try {
            byte[] raw = MAPPER.writeValueAsBytes(payload);
            jdbc.update("INSERT INTO outbox_events (topic, payload) VALUES (?, ?::jsonb)", topic, new String(raw));
        } catch (Exception e) {
            throw new OutboxException("Failed to store outbox event", e);
        }
    }

    /** Background relay that polls and delivers unpublished events. */
    public static final class Relay {
        private final JdbcTemplate jdbc;
        private final Publisher publisher;
        private final Duration interval;
        private final int batchSize;
        private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> { Thread t = new Thread(r, "gkit-outbox-relay"); t.setDaemon(true); return t; });
        private final AtomicBoolean running = new AtomicBoolean(false);

        public Relay(JdbcTemplate jdbc, Publisher publisher) {
            this(jdbc, publisher, Duration.ofSeconds(5), 100);
        }

        public Relay(JdbcTemplate jdbc, Publisher publisher, Duration interval, int batchSize) {
            this.jdbc = jdbc;
            this.publisher = publisher;
            this.interval = interval;
            this.batchSize = batchSize;
        }

        public void start() {
            if (running.compareAndSet(false, true)) {
                scheduler.scheduleAtFixedRate(this::relay, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
            }
        }

        public void stop() {
            running.set(false);
            scheduler.shutdown();
        }

        private void relay() {
            try {
                List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id, topic, payload FROM outbox_events " +
                    "WHERE published_at IS NULL ORDER BY created_at LIMIT ?", batchSize);

                for (Map<String, Object> row : rows) {
                    String id = row.get("id").toString();
                    String topic = (String) row.get("topic");
                    String payload = row.get("payload").toString();

                    publisher.publish(topic, payload.getBytes());
                    jdbc.update("UPDATE outbox_events SET published_at = NOW() WHERE id = ?::uuid", id);
                }
            } catch (Exception e) {
                // Log and continue — next tick will retry
            }
        }
    }

    public static class OutboxException extends RuntimeException {
        public OutboxException(String msg, Throwable cause) { super(msg, cause); }
    }
}
