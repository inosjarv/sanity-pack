package v2.model;

import java.time.Duration;

/**
 * Uniform record for EVERY step - pipeline IO calls and final assertions alike.
 * This uniformity is the key design idea: the report and the summary are just a
 * fold over a List&lt;StepResult&gt;.
 */
public record StepResult(
        String name,
        Kind kind,
        Severity severity,
        Status status,
        int statusCode,     // HTTP status for IO steps; -1 for pure assertions
        String detail,
        Duration elapsed) {

    public boolean passed() { return status == Status.PASS; }

    public static StepResult skipped(String name, Kind kind, Severity severity) {
        return new StepResult(name, kind, severity, Status.SKIP, -1,
                "skipped - earlier step failed", Duration.ZERO);
    }
}
