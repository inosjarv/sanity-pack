package v2.demo;

import sanitypack.v2.check.*;
import sanitypack.v2.model.*;
import sanitypack.v2.pipeline.*;
import sanitypack.v2.report.Reports;
import sanitypack.v2.run.*;
import sanitypack.v2.upstream.*;
import sanitypack.v2.upstream.stub.*;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Runnable demo. No web server, no external services - stubs drive four scenarios
 * that exercise PASS / FAIL / SKIP, warnings, and latest-wins supersession.
 * Run with: java -cp target/classes sanitypack.v2.demo.DemoRunner   (JDK 24+)
 */
public final class DemoRunner {
    private DemoRunner() {}

    public static void main(String[] args) {
        banner("sanity-pack v2 - demo");

        // 1) everything healthy -> PASSED
        run("healthy", buildPack(StubUpstreamService.healthy(), StubReportService.healthy(), 200));

        // 2) a WARNING check fails (audit API returns 503) -> PASSED (with warnings)
        run("degraded-warning", buildPack(StubUpstreamService.healthy(), StubReportService.healthy(), 503));

        // 3) pipeline breaks (report service 503) -> FAILED; downstream stage + all checks SKIPPED
        run("broken-pipeline", buildPack(StubUpstreamService.healthy(), StubReportService.failing(503), 200));

        // 4) supersession: a slow scheduled run is cancelled by a new run
        supersessionDemo();

        banner("done");
    }

    // ---- pack wiring -------------------------------------------------------

    private static SanityPack<FinalResult> buildPack(UpstreamService upstream, ReportService reports, int auditStatus) {
        Supplier<Flow<FinalResult>> pipeline = () -> Flow.<Unit>start(Unit.INSTANCE)
                .then("fetch upstream", Kind.FETCH, u -> {
                    Resp resp = upstream.fetch();
                    return resp.status() == 200
                            ? StageResult.success(resp.body(), 200)
                            : StageResult.failure(resp.status(), "upstream " + resp.status());
                })
                .then("generate reports", Kind.GENERATE, payload -> {
                    Resp resp = reports.generate(payload);
                    return resp.status() == 200
                            ? StageResult.success(resp.body(), 200)
                            : StageResult.failure(resp.status(), "report service " + resp.status());
                })
                .then("assemble final", Kind.GENERATE, reportBody ->
                        StageResult.success(parse(reportBody), -1));

        List<Check<FinalResult>> checks = List.of(
                Checks.assertThat("revenue is non-negative", Severity.CRITICAL,
                        r -> r.revenue() >= 0, r -> "revenue=" + r.revenue()),
                Checks.assertThat("report id present", Severity.CRITICAL,
                        r -> r.reportId() != null && !r.reportId().isBlank(), r -> "missing reportId"),
                Checks.assertThat("has at least 3 rows", Severity.CRITICAL,
                        r -> r.rows().size() >= 3, r -> "only " + r.rows().size() + " rows"),
                Checks.assertThat("revenue passes floor heuristic", Severity.WARNING,
                        r -> r.revenue() > r.rows().size() * 100, r -> "revenue looks low: " + r.revenue()),
                Checks.expectStatus("audit api reachable", Severity.WARNING,
                        () -> auditStatus, 200)   // stubbed 'internal' IO check
        );

        return new SanityPack<>("release-sanity", pipeline, checks, /*maxConcurrency*/ 16);
    }

    private static FinalResult parse(String reportBody) {
        String reportId = field(reportBody, "reportId");
        double revenue = Double.parseDouble(field(reportBody, "revenue"));
        String rowsCsv = field(reportBody, "rows");
        List<String> rows = rowsCsv.isBlank() ? List.of() : List.of(rowsCsv.split(","));
        return new FinalResult(reportId, revenue, rows);
    }

    private static String field(String body, String key) {
        for (String part : body.split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(key)) return part.substring(eq + 1);
        }
        return "";
    }

    // ---- scenario runners --------------------------------------------------

    private static void run(String scenario, SanityPack<FinalResult> pack) {
        System.out.println("\n### scenario: " + scenario);
        Summary s = pack.run();
        System.out.println("response: " + Reports.toRunResponse(s));
        System.out.print(Reports.toMarkdown(pack.name(), s));
    }

    private static void supersessionDemo() {
        System.out.println("\n### scenario: supersession (slow scheduled run cancelled by a new release run)");
        try (RunRegistry registry = new RunRegistry()) {
            SanityPack<FinalResult> slow = buildPack(StubUpstreamService.slow(Duration.ofSeconds(3)), StubReportService.healthy(), 200);
            SanityPack<FinalResult> fast = buildPack(StubUpstreamService.healthy(), StubReportService.healthy(), 200);

            RunRegistry.Run scheduled = registry.trigger("release-sanity", "run-A(scheduled)", "scheduled", slow::run);
            sleep(300);   // a release lands ~300ms into the slow run
            RunRegistry.Run release = registry.trigger("release-sanity", "run-B(release)", "release", fast::run);

            Optional<Summary> a = RunRegistry.await(scheduled);
            Optional<Summary> b = RunRegistry.await(release);

            System.out.println("run-A (scheduled): " +
                    (a.map(Reports::toRunResponse).orElse("SUPERSEDED - discarded, no report")));
            System.out.println("run-B (release):   " +
                    b.map(Reports::toRunResponse).orElse("(unexpectedly empty)"));
        }
    }

    // ---- util --------------------------------------------------------------

    private static void banner(String t) {
        System.out.println("\n==================== " + t + " ====================");
    }
    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
