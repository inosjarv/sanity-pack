package v2.pipeline;

import sanitypack.v2.model.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * A short-circuiting pipeline that keeps a full trail of every step.
 *
 *   Alive&lt;T&gt; - still running; carries the current value + the trail so far
 *   Dead&lt;T&gt;  - a stage failed; carries only the trail (no value)
 *
 * then() runs the next stage only if we are Alive. If already Dead, it records the
 * stage as SKIP and stays Dead. A stage that fails forks Alive-&gt;Dead while keeping
 * the failed step (status code + timing) in the trail - so the report shows exactly
 * where and why the chain stopped. Because value() lives only on Alive, the compiler
 * prevents reading a value out of a failed chain.
 */
public sealed interface Flow<T> permits Flow.Alive, Flow.Dead {

    List<StepResult> trail();

    record Alive<T>(T value, List<StepResult> trail) implements Flow<T> {}
    record Dead<T>(List<StepResult> trail) implements Flow<T> {}

    static <T> Flow<T> start(T seed) { return new Alive<>(seed, List.of()); }

    default <R> Flow<R> then(String name, Kind kind, Stage<? super T, R> stage) {
        return switch (this) {
            case Dead<T> d ->
                    new Dead<>(append(d.trail(), StepResult.skipped(name, kind, Severity.CRITICAL)));
            case Alive<T> a -> {
                long t0 = System.nanoTime();
                StageResult<R> r;
                try {
                    r = stage.run(a.value());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RunCancelled();               // cancellation aborts the whole run
                } catch (RunCancelled e) {
                    throw e;
                } catch (Exception e) {
                    r = StageResult.failure(-1, "threw: " + e);
                }
                var step = new StepResult(name, kind, Severity.CRITICAL,
                        r.ok() ? Status.PASS : Status.FAIL, r.statusCode(), r.detail(),
                        Duration.ofNanos(System.nanoTime() - t0));
                var trail = append(a.trail(), step);
                yield r.ok() ? new Alive<>(r.value(), trail) : new Dead<>(trail);
            }
        };
    }

    default boolean alive() { return this instanceof Alive<T>; }

    private static List<StepResult> append(List<StepResult> base, StepResult s) {
        var copy = new ArrayList<>(base);
        copy.add(s);
        return List.copyOf(copy);
    }
}
