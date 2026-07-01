# sanity-pack v2

A small, functional runner for **post-release sanity checks**. It fetches from an
upstream service, generates a report through a second service (a sequential,
data-dependent chain), then runs a batch of independent checks concurrently on
**virtual threads**, and folds everything into one verdict plus a detailed,
Jira-ready report.

Designed around three ideas:

1. **Every step is the same shape.** Pipeline IO calls and final assertions both
   produce a `StepResult`. The report and the pass/fail summary are just a fold over
   `List<StepResult>`.
2. **The pipeline is a railway.** A failed stage short-circuits the chain but keeps a
   full trail; downstream steps are recorded as `SKIP`, not silently dropped — so the
   report shows exactly where and why it stopped.
3. **Checks fan out, failures are values.** `Gatherers.mapConcurrent` runs the checks
   one-virtual-thread-each with a concurrency cap; a check encodes failure as a value
   (never throws), so one broken check can't cancel the rest.

It is **pure JDK with zero dependencies** — the lightest thing that does the job. A
one-dependency Vavr variant is included if you prefer that vocabulary (see
[`vavr-variant/`](vavr-variant/README-vavr.md)).

## Requirements

- **JDK 24+** — `Gatherers.mapConcurrent` is finalized in Java 24 (JEP 485). Virtual
  threads (21) and sealed types (17) are also used but are older.
- No build tool required (a `run.sh` uses plain `javac`/`java`). A `pom.xml` is
  provided for Maven users.

## Project structure

```
sanity-pack-v2/
├── pom.xml                     # Maven; no required deps (Vavr commented out)
├── run.sh                      # build + run with plain javac/java (no Maven)
├── README.md
├── thought.md                  # design notes, tradeoffs, and production concerns
├── src/main/java/sanitypack/v2/
│   ├── model/                  # Kind, Status, Severity, Outcome, StageResult, StepResult, Summary, RunCancelled
│   ├── check/                  # Check (name + severity + body), Checks (combinators)
│   ├── pipeline/               # Flow (sealed Alive/Dead railway), Stage, Unit
│   ├── run/                    # SanityPack (orchestrator), RunRegistry (supersession/cancel)
│   ├── report/                 # Reports (run-response JSON + Markdown table)
│   ├── upstream/               # service interfaces + Resp/FinalResult + stub/ implementations
│   └── demo/                   # DemoRunner (main)
└── vavr-variant/               # optional Vavr swap-ins (Check, Flow) + notes
```

## Run it

```bash
./run.sh
```

or with Maven:

```bash
mvn -q compile exec:java
```

Both compile the sources and run `DemoRunner`, which drives four scenarios against
stub services (no network, no web server).

## What the demo shows

```
### scenario: healthy
response: {"status":"passed","success":8,"failure":0,"skipped":0}
## Sanity Pack: release-sanity - PASSED
8 passed | 0 failed | 0 skipped | 0 warnings

| Check | Kind | Severity | Result | HTTP | Time |
|---|---|---|---|---|---|
| fetch upstream | FETCH | CRITICAL | pass | 200 | 1 ms |
| generate reports | GENERATE | CRITICAL | pass | 200 | 0 ms |
| assemble final | GENERATE | CRITICAL | pass | - | 0 ms |
| revenue is non-negative | ASSERT | CRITICAL | pass | - | 0 ms |
| report id present | ASSERT | CRITICAL | pass | - | 0 ms |
| has at least 3 rows | ASSERT | CRITICAL | pass | - | 0 ms |
| revenue passes floor heuristic | ASSERT | WARNING | pass | - | 0 ms |
| audit api reachable | ASSERT | WARNING | pass | 200 | 0 ms |

### scenario: degraded-warning
response: {"status":"passed_with_warnings","success":7,"failure":1,"skipped":0}
...
| audit api reachable | ASSERT | WARNING | FAIL | 503 | 2 ms |

### scenario: broken-pipeline
response: {"status":"failed","success":1,"failure":1,"skipped":6}
...
| generate reports | GENERATE | CRITICAL | FAIL | 503 | 0 ms |
| assemble final | GENERATE | CRITICAL | skip | - | 0 ms |
| revenue is non-negative | ASSERT | CRITICAL | skip | - | 0 ms |
...

### scenario: supersession (slow scheduled run cancelled by a new release run)
run-A (scheduled): SUPERSEDED - discarded, no report
run-B (release):   {"status":"passed","success":8,"failure":0,"skipped":0}
```

- **healthy** — pipeline succeeds, every check passes -> `passed`.
- **degraded-warning** — a `WARNING`-severity check fails (audit API 503); no critical
  failure and nothing skipped -> `passed_with_warnings`.
- **broken-pipeline** — the report service returns 503, so `generate reports` fails; the
  remaining stage and all checks are recorded `SKIP` -> `failed`.
- **supersession** — a slow scheduled run is interrupted mid-flight by a newer release
  run; the stale run is discarded (no report/persist), the new one completes.

## Wiring in real services

The stubs implement two interfaces; swap them for real `HttpClient`-backed
implementations. The pipeline code doesn't change:

```java
UpstreamService upstream = () -> {
    HttpResponse<String> r = http.send(
        HttpRequest.newBuilder(URI.create(UPSTREAM_URL))
                   .timeout(Duration.ofSeconds(20))     // bound the known-slow call here
                   .build(),
        HttpResponse.BodyHandlers.ofString());
    return new Resp(r.statusCode(), r.body());
};
```

Use a **single shared** `HttpClient` (it pools connections and owns the virtual-thread
executor). Give each request a `.timeout(...)` — `mapConcurrent` has no built-in
per-task timeout, so the per-request timeout is what bounds a stuck check.

Build a `SanityPack<FinalResult>` from a pipeline supplier and your list of checks, then
run it directly (`pack.run()`) or through `RunRegistry.trigger(...)` if you want
latest-wins cancellation for overlapping/scheduled runs.

## Switching to Vavr

The core stays JDK. If you want Vavr's `Try`/`Seq` at the leaves, uncomment the
dependency in `pom.xml` and copy the two files from `vavr-variant/` over their `src/`
counterparts. They're drop-in (same public API). Details and the full-adoption sketch
are in [`vavr-variant/README-vavr.md`](vavr-variant/README-vavr.md).

## Not included (on purpose)

This is the engine, not the service around it. A production deployment would add: the
HTTP `/run` endpoint (with auth), persistence of runs/steps, Jira posting, a scheduler,
and metrics. The reasoning and recommended shapes for each are in
[`thought.md`](thought.md).
