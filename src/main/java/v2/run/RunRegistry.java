package v2.run;

import sanitypack.v2.model.RunCancelled;
import sanitypack.v2.model.Summary;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Runs sanity packs and enforces LATEST-WINS per pack key: triggering a new run for
 * a key cancels the previous in-flight run for that key.
 *
 * Cancellation is cooperative - cancel(true) interrupts the run's virtual thread; the
 * interrupt aborts the pipeline/checks (surfaced as RunCancelled or an interrupt), and
 * a superseded run yields no result so nothing is reported or persisted for it. Runs
 * are read-only until the very end, so a cancelled run needs no compensation.
 */
public final class RunRegistry implements AutoCloseable {

    public record Run(String runId, String trigger, Instant startedAt,
                      Future<Optional<Summary>> future) {}

    private final ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentHashMap<String, Run> current = new ConcurrentHashMap<>();

    public Run trigger(String packKey, String runId, String trigger, Supplier<Summary> work) {
        Future<Optional<Summary>> future = exec.submit(() -> {
            try {
                Summary s = work.get();
                return Thread.currentThread().isInterrupted() ? Optional.<Summary>empty() : Optional.of(s);
            } catch (Throwable t) {
                if (isCancellation(t)) return Optional.<Summary>empty();
                if (t instanceof RuntimeException re) throw re;
                if (t instanceof Error err) throw err;
                throw new RuntimeException(t);
            } finally {
                // clear the slot only if it is still mine (avoid clobbering a newer run)
                current.compute(packKey, (k, cur) ->
                        cur != null && cur.runId().equals(runId) ? null : cur);
            }
        });
        Run run = new Run(runId, trigger, Instant.now(), future);
        Run prev = current.put(packKey, run);
        if (prev != null) prev.future().cancel(true);   // supersede: interrupt the stale run
        return run;
    }

    /** Await a run's result. A superseded/cancelled run yields Optional.empty(). */
    public static Optional<Summary> await(Run run) {
        try {
            return run.future().get();
        } catch (CancellationException e) {
            return Optional.empty();                     // superseded
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private static boolean isCancellation(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof InterruptedException || c instanceof RunCancelled) return true;
        }
        return Thread.currentThread().isInterrupted();
    }

    @Override public void close() { exec.close(); }
}
