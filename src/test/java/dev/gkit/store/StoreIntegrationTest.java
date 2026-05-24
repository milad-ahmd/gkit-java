package dev.gkit.store;

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
class StoreIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("gkit")
                    .withUsername("gkit")
                    .withPassword("secret");

    static Store store;

    @BeforeAll
    static void setup() {
        store = Store.open(Store.Config.builder()
                .host(postgres.getHost())
                .port(postgres.getMappedPort(5432))
                .database(postgres.getDatabaseName())
                .user(postgres.getUsername())
                .password(postgres.getPassword())
                .build());
    }

    @AfterAll
    static void teardown() {
        store = null;
    }

    @Test
    void pingSucceeds() {
        assertDoesNotThrow(() -> store.query("SELECT 1"));
    }

    @Test
    void insertAndQueryRow() {
        store.update("CREATE TABLE IF NOT EXISTS st_items (id SERIAL PRIMARY KEY, name TEXT NOT NULL)");
        store.update("INSERT INTO st_items (name) VALUES (?)", "hello");

        List<Map<String, Object>> rows = store.query("SELECT name FROM st_items WHERE name = ?", "hello");
        assertEquals(1, rows.size());
        assertEquals("hello", rows.get(0).get("name"));
    }

    @Test
    void transactionCommits() {
        store.update("CREATE TABLE IF NOT EXISTS st_commit (val TEXT NOT NULL)");

        store.withTx(jdbc -> {
            jdbc.update("INSERT INTO st_commit (val) VALUES (?)", "committed");
            return null;
        });

        List<Map<String, Object>> rows = store.query("SELECT val FROM st_commit");
        assertEquals(1, rows.size());
        assertEquals("committed", rows.get(0).get("val"));
    }

    @Test
    void transactionRollsBackOnException() {
        store.update("CREATE TABLE IF NOT EXISTS st_rollback (val TEXT NOT NULL)");

        assertThrows(RuntimeException.class, () -> store.withTx(jdbc -> {
            jdbc.update("INSERT INTO st_rollback (val) VALUES (?)", "should-not-exist");
            throw new RuntimeException("intentional rollback");
        }));

        List<Map<String, Object>> rows = store.query("SELECT * FROM st_rollback");
        assertEquals(0, rows.size(), "Row should have been rolled back");
    }
}
