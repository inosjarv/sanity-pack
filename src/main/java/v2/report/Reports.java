package v2.report;

import sanitypack.v2.model.*;

import java.util.stream.Collectors;

/** Renders a Summary as a compact /run response and as a Jira-ready Markdown report. */
public final class Reports {
    private Reports() {}

    /** The compact JSON line the /run endpoint returns. */
    public static String toRunResponse(Summary s) {
        return "{\"status\":\"%s\",\"success\":%d,\"failure\":%d,\"skipped\":%d}"
                .formatted(s.statusLabel(), s.success(), s.failure(), s.skipped());
    }

    /** The detailed report to attach to a Jira ticket or store in the DB. */
    public static String toMarkdown(String packName, Summary s) {
        String badge = switch (s.statusLabel()) {
            case "passed" -> "PASSED";
            case "passed_with_warnings" -> "PASSED (with warnings)";
            default -> "FAILED";
        };
        String head = """
                ## Sanity Pack: %s - %s
                %d passed | %d failed | %d skipped | %d warnings

                | Check | Kind | Severity | Result | HTTP | Time |
                |---|---|---|---|---|---|
                """.formatted(packName, badge, s.success(), s.failure(), s.skipped(), s.warnings());
        String rows = s.steps().stream().map(r -> "| %s | %s | %s | %s | %s | %d ms |".formatted(
                r.name(), r.kind(), r.severity(), symbol(r.status()),
                r.statusCode() < 0 ? "-" : String.valueOf(r.statusCode()),
                r.elapsed().toMillis())).collect(Collectors.joining("\n"));
        return head + rows + "\n";
    }

    private static String symbol(Status st) {
        return switch (st) { case PASS -> "pass"; case FAIL -> "FAIL"; case SKIP -> "skip"; };
    }
}
