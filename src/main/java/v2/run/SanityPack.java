package v2.run;

import sanitypack.v2.check.Check;
import sanitypack.v2.model.*;
import sanitypack.v2.pipeline.Flow;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Gatherers;

/**
 * Orchestrates one sanity run:
 *   1. run the (sequential, data-dependent) pipeline to assemble the result
 *   2. fan out the (independent) checks concurrently on virtual threads
 *   3. fold everything into a Summary
 *
 * If the pipeline breaks, the checks are recorded as SKIP rather than silently
 * omitted, so the report is explicit about what did not get a chance to run.
 */
public final class SanityPack<T> {

    private final String name;
    private final Supplier<Flow<T>> pipeline;
    private final List<Check<T>> checks;
    private final int maxConcurrency;

    public SanityPack(String name, Supplier<Flow<T>> pipeline, List<Check<T>> checks, int maxConcurrency) {
        this.name = name;
        this.pipeline = pipeline;
        this.checks = checks;
        this.maxConcurrency = Math.max(1, maxConcurrency);
    }

    public String name() { return name; }

    public Summary run() {
        Flow<T> flow = pipeline.get();

        List<StepResult> checkResults = switch (flow) {
            case Flow.Alive<T> a -> runChecks(a.value());          // pipeline survived -> run checks
            case Flow.Dead<T> d -> checks.stream()                 // pipeline broke -> mark all skipped
                    .map(c -> StepResult.skipped(c.name(), Kind.ASSERT, c.severity()))
                    .toList();
        };

        return Summary.of(concat(flow.trail(), checkResults));
    }

    private List<StepResult> runChecks(T result) {
        // mapConcurrent: one virtual thread per check, capped at maxConcurrency, encounter
        // order preserved. Checks encode failure as a value, so a failing check never
        // short-circuits the others. No global timeout here - bound each real HTTP call
        // with HttpClient's per-request .timeout(...) instead (see README/thought.md).
        return checks.stream()
                .gather(Gatherers.mapConcurrent(maxConcurrency, c -> c.evaluate(result)))
                .toList();
    }

    private static <E> List<E> concat(List<E> a, List<E> b) {
        var out = new ArrayList<E>(a.size() + b.size());
        out.addAll(a);
        out.addAll(b);
        return List.copyOf(out);
    }
}
