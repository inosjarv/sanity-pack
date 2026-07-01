package v2.check;

import sanitypack.v2.model.*;

import java.util.function.Function;
import java.util.function.Predicate;

/** Combinators for building checks declaratively. */
public final class Checks {
    private Checks() {}

    /** A pure assertion on the assembled result. */
    public static <T> Check<T> assertThat(String name, Severity severity,
                                          Predicate<T> predicate, Function<T, String> describe) {
        return Check.of(name, severity, input ->
                predicate.test(input) ? Outcome.pass("ok") : Outcome.fail(describe.apply(input)));
    }

    /** An IO check that calls something returning an HTTP-like status code. */
    public static <T> Check<T> expectStatus(String name, Severity severity,
                                            StatusCall call, int expected) {
        return Check.of(name, severity, input -> {
            int code = call.get();
            return code == expected ? Outcome.pass(code)
                    : Outcome.fail(code, "expected " + expected + " but got " + code);
        });
    }

    @FunctionalInterface
    public interface StatusCall { int get() throws Exception; }
}
