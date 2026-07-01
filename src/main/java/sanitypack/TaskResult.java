package sanitypack;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * The outcome of running a Task. Holds overall status, where it failed,
 * the final context (so you can read produced values), and a per-step
 * report - handy if "sanity pack" means running a pack of checks and
 * showing which passed/failed.
 */
public final class TaskResult {

    public enum Status { SUCCESS, FAILED }

    public static final class StepRecord {
        public final String name;
        public final boolean succeeded;
        public final boolean skipped;
        public final int attempts;
        public final Duration elapsed;
        public final Throwable error; // null unless failed

        StepRecord(String name, boolean succeeded, boolean skipped,
                   int attempts, Duration elapsed, Throwable error) {
            this.name = name;
            this.succeeded = succeeded;
            this.skipped = skipped;
            this.attempts = attempts;
            this.elapsed = elapsed;
            this.error = error;
        }
    }

    private final Status status;
    private final String failedStep;   // null on success
    private final Throwable error;      // null on success
    private final Duration elapsed;
    private final TaskContext context;
    private final List<StepRecord> steps;

    TaskResult(Status status, String failedStep, Throwable error,
               Duration elapsed, TaskContext context, List<StepRecord> steps) {
        this.status = status;
        this.failedStep = failedStep;
        this.error = error;
        this.elapsed = elapsed;
        this.context = context;
        this.steps = steps;
    }

    public boolean isSuccess() { return status == Status.SUCCESS; }
    public Status status() { return status; }
    public Optional<String> failedStep() { return Optional.ofNullable(failedStep); }
    public Optional<Throwable> error() { return Optional.ofNullable(error); }
    public Duration elapsed() { return elapsed; }
    public TaskContext context() { return context; }
    public List<StepRecord> steps() { return steps; }
}
