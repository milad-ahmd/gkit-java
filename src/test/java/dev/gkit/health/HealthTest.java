package dev.gkit.health;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class HealthTest {

    @Test
    @DisplayName("all passing checkers produce healthy report")
    void allPassingCheckersProduceHealthyReport() {
        Health.Group group = Health.Group.builder()
                .checkTimeout(Duration.ofSeconds(5))
                .build();
        group.register("db", () -> {}); // no-op = healthy
        group.register("cache", () -> {});

        Health.Report report = group.check();

        assertTrue(report.healthy());
        assertEquals(2, report.checks().size());
        report.checks().forEach(s -> assertTrue(s.healthy()));
    }

    @Test
    @DisplayName("a failing checker makes report unhealthy")
    void failingCheckerMakesReportUnhealthy() {
        Health.Group group = Health.Group.builder()
                .checkTimeout(Duration.ofSeconds(5))
                .build();
        group.register("db", () -> {});
        group.register("broken", () -> { throw new RuntimeException("connection refused"); });

        Health.Report report = group.check();

        assertFalse(report.healthy());
        long unhealthyCount = report.checks().stream().filter(s -> !s.healthy()).count();
        assertEquals(1, unhealthyCount);
    }

    @Test
    @DisplayName("failing checker Status captures error message")
    void failingCheckerCapturesErrorMessage() {
        Health.Group group = Health.Group.builder()
                .checkTimeout(Duration.ofSeconds(5))
                .build();
        group.register("broken", () -> { throw new RuntimeException("timeout"); });

        Health.Report report = group.check();

        Health.Status broken = report.checks().stream()
                .filter(s -> s.name().equals("broken"))
                .findFirst()
                .orElseThrow();

        assertFalse(broken.healthy());
        assertEquals("timeout", broken.error());
    }

    @Test
    @DisplayName("passing checker Status has null error")
    void passingCheckerHasNullError() {
        Health.Group group = Health.Group.builder()
                .checkTimeout(Duration.ofSeconds(5))
                .build();
        group.register("ok", () -> {});

        Health.Report report = group.check();

        Health.Status ok = report.checks().get(0);
        assertTrue(ok.healthy());
        assertNull(ok.error());
    }

    @Test
    @DisplayName("group with no checkers returns healthy empty report")
    void emptyGroupReturnsHealthyReport() {
        Health.Group group = Health.Group.builder().build();
        Health.Report report = group.check();

        assertTrue(report.healthy());
        assertTrue(report.checks().isEmpty());
    }

    @Test
    @DisplayName("timed-out checker contributes unhealthy status")
    void timedOutCheckerMakesUnhealthy() throws InterruptedException {
        Health.Group group = Health.Group.builder()
                .checkTimeout(Duration.ofMillis(50))
                .build();
        group.register("slow", () -> Thread.sleep(5000));

        Health.Report report = group.check();

        assertFalse(report.healthy());
        Health.Status slow = report.checks().get(0);
        assertFalse(slow.healthy());
        assertEquals("timeout", slow.error());
    }

    @Test
    @DisplayName("Status record carries name, healthy, and error fields")
    void statusRecordFields() {
        Health.Status s = new Health.Status("my-service", false, "connection error");
        assertEquals("my-service", s.name());
        assertFalse(s.healthy());
        assertEquals("connection error", s.error());
    }

    @Test
    @DisplayName("Report.duration is non-blank string")
    void reportDurationIsNonBlank() {
        Health.Group group = Health.Group.builder().build();
        group.register("quick", () -> {});
        Health.Report report = group.check();
        assertNotNull(report.duration());
        assertFalse(report.duration().isBlank());
    }

    @Test
    @DisplayName("multiple concurrent checkers all run")
    void multipleConcurrentCheckersAllRun() {
        int checkersCount = 5;
        Health.Group group = Health.Group.builder()
                .checkTimeout(Duration.ofSeconds(5))
                .build();
        for (int i = 0; i < checkersCount; i++) {
            final int idx = i;
            group.register("checker-" + idx, () -> {});
        }

        Health.Report report = group.check();

        assertTrue(report.healthy());
        assertEquals(checkersCount, report.checks().size());
    }

    @Test
    @DisplayName("mix of healthy and unhealthy checkers produces overall unhealthy report")
    void mixedCheckersProduceUnhealthyReport() {
        Health.Group group = Health.Group.builder()
                .checkTimeout(Duration.ofSeconds(5))
                .build();
        group.register("good1", () -> {});
        group.register("bad", () -> { throw new RuntimeException("fail"); });
        group.register("good2", () -> {});

        Health.Report report = group.check();

        assertFalse(report.healthy());
        assertEquals(3, report.checks().size());
        long healthyCount = report.checks().stream().filter(Health.Status::healthy).count();
        assertEquals(2, healthyCount);
    }
}
