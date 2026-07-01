package v2.check;

import sanitypack.v2.model.*;

import java.time.Duration;

/**
 * A sanity check is a VALUE: a name + severity + a function from the assembled
 * result to an Outcome. The runner wraps evaluation with timing and exception
 * capture, so a check body never has to think about either.
 *
 * IMPORTANT: evaluate() never throws for business failures - a thrown exception
 * becomes a FAIL Outcome. This is what makes the concurrent fan-out safe:
 * Gatherers.mapConcurrent is fail-fast on thrown exceptions, so encoding failure
 * as a *value* means one broken check cannot cancel the other 29.
 *
 * The one thing we DO let escape is cancellation (InterruptedException /
 * RunCancelled), so a superseded run aborts instead of producing a bogus report.
 */
public record Check<T>(String name, Severity severity, Body<T> body) {

    /** A check body may throw checked exceptions (e.g. IOException from an HTTP call). */
    @FunctionalInterface
    public interface Body<T> { Outcome apply(T input) throws Exception; }

    public static <T> Check<T> of(String name, Severity severity, Body<T> body) {
        return new Check<>(name, severity, body);
    }

    public StepResult evaluate(T input) {
        long t0 = System.nanoTime();
        Outcome outcome;
        try {
            outcome = body.apply(input);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RunCancelled();                      // cancellation escapes
        } catch (RunCancelled e) {
            throw e;
        } catch (Exception e) {
            outcome = Outcome.fail("threw: " + e);         // business failure -> FAIL value
        }
        return new StepResult(name, Kind.ASSERT, severity,
                outcome.passed() ? Status.PASS : Status.FAIL,
                outcome.statusCode(), outcome.detail(),
                Duration.ofNanos(System.nanoTime() - t0));
    }
}
