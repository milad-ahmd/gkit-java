package dev.gkit.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Postgres-backed durable job queue with retry and dead-letter support.
 *
 * <p>Schema:
 * <pre>{@code
 * CREATE TABLE jobs (
 *     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 *     type TEXT NOT NULL, payload JSONB NOT NULL,
 *     status TEXT NOT NULL DEFAULT 'pending',
 *     attempts INT NOT NULL DEFAULT 0,
 *     max_attempts INT NOT NULL DEFAULT 3,
 *     run_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *     last_error TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * }</pre>
 *
 * <pre>{@code
 * Queue q = new Queue(jdbcTemplate);
 * q.register("send-email", payload -> mailer.send(payload.decode(EmailJob.class)));
 * q.enqueue("send-email", new EmailJob(order.email));
 * q.start(4);
 * }</pre>
 */
public final class Queue {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbc;
    private final Map<String, JobHandler> handlers = new ConcurrentHashMap<>();
    private final Duration pollInterval;
    private ScheduledExecutorService scheduler;

    public Queue(JdbcTemplate jdbc) { this(jdbc, Duration.ofSeconds(2)); }
    public Queue(JdbcTemplate jdbc, Duration pollInterval) {
        this.jdbc = jdbc; this.pollInterval = pollInterval;
    }

    @FunctionalInterface
    public interface JobHandler {
        void handle(Payload payload) throws Exception;
    }

    public record Payload(String raw) {
        public <T> T decode(Class<T> cls) throws Exception { return MAPPER.readValue(raw, cls); }
    }

    public void register(String jobType, JobHandler handler) { handlers.put(jobType, handler); }

    public void enqueue(String jobType, Object payload) { enqueue(jobType, payload, 3, Duration.ZERO); }

    public void enqueue(String jobType, Object payload, int maxAttempts, Duration delay) {
        try {
            String raw = MAPPER.writeValueAsString(payload);
            Instant runAt = Instant.now().plus(delay);
            jdbc.update("INSERT INTO jobs (type, payload, max_attempts, run_at) VALUES (?, ?::jsonb, ?, ?)",
                jobType, raw, maxAttempts, java.sql.Timestamp.from(runAt));
        } catch (Exception e) { throw new RuntimeException("Failed to enqueue job", e); }
    }

    public void start(int workers) {
        scheduler = Executors.newScheduledThreadPool(workers);
        for (int i = 0; i < workers; i++) {
            scheduler.scheduleAtFixedRate(this::processBatch, 0, pollInterval.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    public void stop() { if (scheduler != null) scheduler.shutdown(); }

    private void processBatch() {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, type, payload, attempts, max_attempts FROM jobs " +
            "WHERE status='pending' AND run_at <= NOW() ORDER BY run_at LIMIT 10 " +
            "FOR UPDATE SKIP LOCKED");

        for (Map<String, Object> row : rows) {
            String id = row.get("id").toString();
            String type = (String) row.get("type");
            String payloadStr = row.get("payload").toString();
            int attempts = ((Number) row.get("attempts")).intValue();
            int maxAttempts = ((Number) row.get("max_attempts")).intValue();

            JobHandler handler = handlers.get(type);
            if (handler == null) {
                jdbc.update("UPDATE jobs SET status='failed', last_error=? WHERE id=?::uuid",
                    "no handler for type: " + type, id);
                continue;
            }
            try {
                handler.handle(new Payload(payloadStr));
                jdbc.update("UPDATE jobs SET status='done', attempts=? WHERE id=?::uuid", attempts + 1, id);
            } catch (Exception e) {
                int nextAttempts = attempts + 1;
                if (nextAttempts >= maxAttempts) {
                    jdbc.update("UPDATE jobs SET status='dead', attempts=?, last_error=? WHERE id=?::uuid",
                        nextAttempts, e.getMessage(), id);
                } else {
                    long backoffSecs = (long) Math.pow(2, nextAttempts) * 10;
                    Instant runAt = Instant.now().plusSeconds(Math.min(backoffSecs, 3600));
                    jdbc.update("UPDATE jobs SET status='pending', attempts=?, last_error=?, run_at=? WHERE id=?::uuid",
                        nextAttempts, e.getMessage(), java.sql.Timestamp.from(runAt), id);
                }
            }
        }
    }
}
