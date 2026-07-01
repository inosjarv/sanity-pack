package sanitypack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * An ordered sequence of Steps sharing one TaskContext. Steps run one
 * after another; each can see everything produced before it. A Task can
 * also be nested inside another Task as a single step, giving you the
 * task / subtask hierarchy ("so on and so forth").
 */
public final class Task {

    private final String name;
    private final List<Step> steps;
    private final StepListener listener;

    private Task(String name, List<Step> steps, StepListener listener) {
        this.name = name;
        this.steps = steps;
        this.listener = listener;
    }

    public static Builder named(String name) { return new Builder(name); }

    public String name() { return name; }

    /** Run with a fresh context. */
    public TaskResult execute() { return execute(new TaskContext()); }

    /** Run against an existing context (also used when nesting tasks). */
    public TaskResult execute(TaskContext context) {
        long taskStart = System.nanoTime();
        List<TaskResult.StepRecord> records = new ArrayList<>();

        for (Step step : steps) {
            if (!step.shouldRun(context)) {
                listener.onSkip(step.name());
                records.add(new TaskResult.StepRecord(step.name(), true, true, 0, Duration.ZERO, null));
                continue;
            }

            long stepStart = System.nanoTime();
            Throwable last = null;
            int attempt = 0;
            boolean ok = false;

            while (attempt < step.maxAttempts()) {
                attempt++;
                try {
                    listener.onStart(step.name(), attempt);
                    step.action().run(context);
                    ok = true;
                    listener.onSuccess(step.name(), attempt);
                    break;
                } catch (Throwable t) {
                    last = t;
                    if (attempt < step.maxAttempts()) {
                        listener.onRetry(step.name(), attempt, t);
                        sleepBackoff(step.baseBackoff(), attempt);
                    }
                }
            }

            Duration stepElapsed = Duration.ofNanos(System.nanoTime() - stepStart);

            if (ok) {
                records.add(new TaskResult.StepRecord(step.name(), true, false, attempt, stepElapsed, null));
            } else {
                records.add(new TaskResult.StepRecord(step.name(), false, false, attempt, stepElapsed, last));
                listener.onFailure(step.name(), attempt, last);
                if (!step.optional()) {
                    Duration elapsed = Duration.ofNanos(System.nanoTime() - taskStart);
                    return new TaskResult(TaskResult.Status.FAILED, step.name(), last, elapsed, context, records);
                }
                // optional step failed -> record it and keep going
            }
        }

        Duration elapsed = Duration.ofNanos(System.nanoTime() - taskStart);
        return new TaskResult(TaskResult.Status.SUCCESS, null, null, elapsed, context, records);
    }

    /** Exponential backoff with light jitter to avoid thundering herds. */
    private static void sleepBackoff(Duration base, int attempt) {
        long millis = (long) (base.toMillis() * Math.pow(2, attempt - 1));
        long jitter = (long) (Math.random() * base.toMillis());
        try {
            Thread.sleep(millis + jitter);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public static final class Builder {
        private final String name;
        private final List<Step> steps = new ArrayList<>();
        private StepListener listener = StepListener.NOOP;

        private Builder(String name) { this.name = name; }

        public Builder step(Step step) { steps.add(step); return this; }

        public Builder step(String name, Subtask action) {
            steps.add(Step.of(name, action));
            return this;
        }

        /**
         * Nest another Task as a single step. It shares the SAME context,
         * so data flows across the boundary. If the sub-task fails, this
         * step fails (and respects the usual retry / optional policy if you
         * wrap it via step(Step) instead).
         */
        public Builder subTask(Task subTask) {
            steps.add(Step.of(subTask.name(), ctx -> {
                TaskResult r = subTask.execute(ctx);
                if (!r.isSuccess()) {
                    throw new RuntimeException(
                        "Sub-task '" + subTask.name() + "' failed at step '"
                        + r.failedStep().orElse("?") + "'",
                        r.error().orElse(null));
                }
            }));
            return this;
        }

        public Builder listener(StepListener listener) {
            this.listener = (listener == null) ? StepListener.NOOP : listener;
            return this;
        }

        public Task build() { return new Task(name, List.copyOf(steps), listener); }
    }
}
