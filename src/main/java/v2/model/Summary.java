package v2.model;

import java.util.List;

/**
 * The folded verdict over all steps.
 * The pack passes iff there are no CRITICAL failures and nothing was skipped.
 * WARNING failures are surfaced (passedWithWarnings) but do not fail the pack.
 */
public record Summary(
        boolean passed,
        boolean passedWithWarnings,
        int success,
        int failure,
        int skipped,
        int warnings,
        List<StepResult> steps) {

    public static Summary of(List<StepResult> steps) {
        int pass = 0, fail = 0, skip = 0, warn = 0;
        boolean criticalFailure = false;
        for (StepResult s : steps) {
            switch (s.status()) {
                case PASS -> pass++;
                case SKIP -> skip++;
                case FAIL -> {
                    fail++;
                    if (s.severity() == Severity.CRITICAL) criticalFailure = true;
                    else warn++;
                }
            }
        }
        boolean hardPass = !criticalFailure && skip == 0;
        boolean withWarnings = hardPass && warn > 0;
        return new Summary(hardPass, withWarnings, pass, fail, skip, warn, steps);
    }

    /** Status string for the /run response: passed | passed_with_warnings | failed. */
    public String statusLabel() {
        if (!passed) return "failed";
        return passedWithWarnings ? "passed_with_warnings" : "passed";
    }
}
