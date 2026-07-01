package sanitypack;

import java.time.Duration;
import java.util.function.Predicate;

/**
 * A Subtask plus how to run it: retries, whether failure is fatal, and
 * an optional guard condition. Separating the "what" (Subtask) from the
 * "how" (Step) keeps your call logic clean and testable.
 *
 *   Step.builder("fetchOrders", ctx -> { ... })
 *       .retry(3, Duration.ofMillis(200))   // 3 total attempts, exp backoff
 *       .runIf(ctx -> ctx.has(USER_ID))      // skip if precondition unmet
 *       .optional()                          // failure won't abort the task
 *       .build();
 */
public final class Step {

    private final String name;
    private final Subtask action;
    private final int maxAttempts;            // total attempts (>= 1)
    private final Duration baseBackoff;
    private final boolean optional;           // if true, failure is non-fatal
    private final Predicate<TaskContext> runIf;

    private Step(Builder b) {
        this.name = b.name;
        this.action = b.action;
        this.maxAttempts = b.maxAttempts;
        this.baseBackoff = b.baseBackoff;
        this.optional = b.optional;
        this.runIf = b.runIf;
    }

    public static Step of(String name, Subtask action) {
        return builder(name, action).build();
    }

    public static Builder builder(String name, Subtask action) {
        return new Builder(name, action);
    }

    public String name() { return name; }
    public Subtask action() { return action; }
    public int maxAttempts() { return maxAttempts; }
    public Duration baseBackoff() { return baseBackoff; }
    public boolean optional() { return optional; }
    public boolean shouldRun(TaskContext ctx) { return runIf == null || runIf.test(ctx); }

    public static final class Builder {
        private final String name;
        private final Subtask action;
        private int maxAttempts = 1;
        private Duration baseBackoff = Duration.ofMillis(200);
        private boolean optional = false;
        private Predicate<TaskContext> runIf = null;

        private Builder(String name, Subtask action) {
            this.name = name;
            this.action = action;
        }

        /** Total attempts including the first try. */
        public Builder retry(int maxAttempts) {
            this.maxAttempts = Math.max(1, maxAttempts);
            return this;
        }

        public Builder retry(int maxAttempts, Duration baseBackoff) {
            this.maxAttempts = Math.max(1, maxAttempts);
            this.baseBackoff = baseBackoff;
            return this;
        }

        /** Failure of this step will be recorded but won't abort the Task. */
        public Builder optional() { this.optional = true; return this; }

        /** Step runs only if the condition holds; otherwise it's skipped. */
        public Builder runIf(Predicate<TaskContext> condition) {
            this.runIf = condition;
            return this;
        }

        public Step build() { return new Step(this); }
    }
}
