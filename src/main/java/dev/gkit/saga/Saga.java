package dev.gkit.saga;

import java.util.*;

/**
 * Saga pattern for distributed transaction management with compensating actions.
 *
 * <pre>{@code
 * Saga saga = Saga.named("place-order",
 *     Saga.step("reserve-inventory")
 *         .execute(() -> inventory.reserve(item))
 *         .compensate(() -> inventory.release(item))
 *         .build(),
 *     Saga.step("charge-payment")
 *         .execute(() -> payments.charge(amount))
 *         .compensate(() -> payments.refund(amount))
 *         .build()
 * );
 *
 * try { saga.run(); }
 * catch (Saga.SagaException e) {
 *     log.error("Saga failed at {}: {}", e.getFailedStep(), e.getCause());
 * }
 * }</pre>
 */
public final class Saga {

    private final String name;
    private final List<Step> steps;

    private Saga(String name, List<Step> steps) {
        this.name = name;
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
    }

    // -------------------------------------------------------------------------
    // Step

    @FunctionalInterface
    public interface Action {
        void run() throws Exception;
    }

    public static final class Step {
        private final String name;
        private final Action execute;
        private final Action compensate;

        private Step(StepBuilder b) {
            this.name = b.name;
            this.execute = b.execute;
            this.compensate = b.compensate;
        }

        public String getName() { return name; }
    }

    public static StepBuilder step(String name) { return new StepBuilder(name); }

    public static final class StepBuilder {
        private final String name;
        private Action execute;
        private Action compensate;

        StepBuilder(String name) { this.name = name; }

        public StepBuilder execute(Action a) { this.execute = a; return this; }
        public StepBuilder compensate(Action a) { this.compensate = a; return this; }
        public Step build() {
            Objects.requireNonNull(execute, "execute action must be set");
            return new Step(this);
        }
    }

    // -------------------------------------------------------------------------
    // Factory

    public static Saga named(String name, Step... steps) {
        return new Saga(name, Arrays.asList(steps));
    }

    // -------------------------------------------------------------------------
    // Execution

    public void run() {
        List<Integer> completed = new ArrayList<>();

        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            try {
                step.execute.run();
                completed.add(i);
            } catch (Exception e) {
                List<CompensationError> compErrors = compensate(completed);
                throw new SagaException(name, step.getName(), e, compErrors);
            }
        }
    }

    private List<CompensationError> compensate(List<Integer> completed) {
        List<CompensationError> errors = new ArrayList<>();
        for (int i = completed.size() - 1; i >= 0; i--) {
            Step step = steps.get(completed.get(i));
            if (step.compensate == null) continue;
            try {
                step.compensate.run();
            } catch (Exception e) {
                errors.add(new CompensationError(step.getName(), e));
            }
        }
        return errors;
    }

    // -------------------------------------------------------------------------
    // Errors

    public record CompensationError(String step, Exception error) {}

    public static final class SagaException extends RuntimeException {
        private final String sagaName;
        private final String failedStep;
        private final List<CompensationError> compensationErrors;

        SagaException(String sagaName, String failedStep, Exception cause, List<CompensationError> compErrors) {
            super("Saga '" + sagaName + "' failed at step '" + failedStep + "': " + cause.getMessage(), cause);
            this.sagaName = sagaName;
            this.failedStep = failedStep;
            this.compensationErrors = compErrors == null ? Collections.emptyList() : compErrors;
        }

        public String getSagaName() { return sagaName; }
        public String getFailedStep() { return failedStep; }
        public List<CompensationError> getCompensationErrors() { return compensationErrors; }
        public boolean hasCompensationErrors() { return !compensationErrors.isEmpty(); }
    }
}
