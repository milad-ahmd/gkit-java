package dev.gkit.saga;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SagaTest {

    @Test
    @DisplayName("saga with all successful steps runs to completion")
    void allSuccessfulSteps() {
        AtomicInteger count = new AtomicInteger(0);
        Saga saga = Saga.named("happy-path",
                Saga.step("step1").execute(count::incrementAndGet).build(),
                Saga.step("step2").execute(count::incrementAndGet).build()
        );
        assertDoesNotThrow(saga::run);
        assertEquals(2, count.get());
    }

    @Test
    @DisplayName("saga throws SagaException when a step fails")
    void throwsSagaExceptionOnStepFailure() {
        Saga saga = Saga.named("failing-saga",
                Saga.step("step1").execute(() -> {}).build(),
                Saga.step("step2").execute(() -> { throw new RuntimeException("step2 fails"); }).build()
        );
        Saga.SagaException ex = assertThrows(Saga.SagaException.class, saga::run);
        assertEquals("failing-saga", ex.getSagaName());
        assertEquals("step2", ex.getFailedStep());
    }

    @Test
    @DisplayName("compensating actions run in reverse order on failure")
    void compensatingActionsRunInReverseOrder() {
        StringBuilder order = new StringBuilder();

        Saga saga = Saga.named("compensation-order",
                Saga.step("step1")
                        .execute(() -> order.append("exec1,"))
                        .compensate(() -> order.append("comp1,"))
                        .build(),
                Saga.step("step2")
                        .execute(() -> order.append("exec2,"))
                        .compensate(() -> order.append("comp2,"))
                        .build(),
                Saga.step("step3")
                        .execute(() -> { throw new RuntimeException("fail at step3"); })
                        .compensate(() -> order.append("comp3,"))
                        .build()
        );

        assertThrows(Saga.SagaException.class, saga::run);
        String result = order.toString();
        // exec1 and exec2 ran; compensation runs in reverse: comp2 then comp1
        assertTrue(result.contains("exec1,"), "step1 should have executed");
        assertTrue(result.contains("exec2,"), "step2 should have executed");
        int comp2Pos = result.indexOf("comp2,");
        int comp1Pos = result.indexOf("comp1,");
        assertTrue(comp2Pos < comp1Pos, "comp2 should run before comp1 (reverse order)");
    }

    @Test
    @DisplayName("steps without compensate action are skipped during compensation")
    void stepWithoutCompensateIsSkipped() {
        AtomicBoolean compensated = new AtomicBoolean(false);

        Saga saga = Saga.named("no-compensate",
                Saga.step("step1")
                        .execute(() -> {})
                        // No compensate action
                        .build(),
                Saga.step("step2")
                        .execute(() -> {})
                        .compensate(() -> compensated.set(true))
                        .build(),
                Saga.step("step3")
                        .execute(() -> { throw new RuntimeException("fail"); })
                        .build()
        );

        assertThrows(Saga.SagaException.class, saga::run);
        // step2's compensation should have run
        assertTrue(compensated.get());
    }

    @Test
    @DisplayName("first step failure triggers no compensations")
    void firstStepFailureNoCompensation() {
        AtomicBoolean compensationCalled = new AtomicBoolean(false);
        Saga saga = Saga.named("first-fails",
                Saga.step("step1")
                        .execute(() -> { throw new RuntimeException("first fails"); })
                        .compensate(() -> compensationCalled.set(true))
                        .build()
        );
        assertThrows(Saga.SagaException.class, saga::run);
        // step1 failed without completing, so no compensation runs
        assertFalse(compensationCalled.get());
    }

    @Test
    @DisplayName("StepBuilder requires execute action")
    void stepBuilderRequiresExecuteAction() {
        assertThrows(NullPointerException.class, () ->
                Saga.step("no-execute").build()
        );
    }

    @Test
    @DisplayName("Step.getName returns configured name")
    void stepGetNameReturnsConfiguredName() {
        Saga.Step step = Saga.step("my-step").execute(() -> {}).build();
        assertEquals("my-step", step.getName());
    }

    @Test
    @DisplayName("saga with no steps runs without error")
    void sagaWithNoStepsRunsCleanly() {
        Saga saga = Saga.named("empty-saga");
        assertDoesNotThrow(saga::run);
    }

    @Test
    @DisplayName("SagaException wraps the original cause")
    void sagaExceptionWrapsOriginalCause() {
        RuntimeException original = new RuntimeException("root cause");
        Saga saga = Saga.named("test",
                Saga.step("s").execute(() -> { throw original; }).build()
        );
        Saga.SagaException ex = assertThrows(Saga.SagaException.class, saga::run);
        assertSame(original, ex.getCause());
    }

    @Test
    @DisplayName("SagaException.hasCompensationErrors is false when compensation succeeds")
    void hasCompensationErrorsFalseWhenCompensationSucceeds() {
        Saga saga = Saga.named("test",
                Saga.step("s1").execute(() -> {}).compensate(() -> {}).build(),
                Saga.step("s2").execute(() -> { throw new RuntimeException("fail"); }).build()
        );
        Saga.SagaException ex = assertThrows(Saga.SagaException.class, saga::run);
        assertFalse(ex.hasCompensationErrors());
    }
}
