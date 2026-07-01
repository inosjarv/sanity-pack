# thought.md — reasoning, tradeoffs, and things to watch

Honest notes on why this is built the way it is, and what I'd want you to know before
running it anywhere real.

## On the deliverable: pure-JDK primary, Vavr optional

You asked to use Vavr if feasible. It is feasible — it's a single jar with no transitive
dependencies, and it just reached a stable **1.0.1** (early 2026), so the old "is it
maintained?" worry is largely gone. But two things pushed me to ship the **pure-JDK**
version as the primary and keep Vavr as a drop-in variant:

1. **Your first and strongest constraint was "lightweight, not many dependencies."** A
   zero-dependency core is the most literal satisfaction of that. And on a modern JDK,
   records + sealed types + `switch` patterns + virtual threads + `Gatherers` already
   cover most of what Vavr historically provided here. Vavr's remaining genuine wins are
   narrow: `Try` at the leaves and persistent `Seq` for the trail.
2. **I could fully verify the JDK version and could not verify the Vavr version.** In the
   environment I built this in, Maven Central was firewalled, so I couldn't compile or run
   anything that imports `io.vavr`. I *could* install JDK 25 and compile+run the pure-JDK
   project end-to-end — the demo output in the README is real. I didn't want to hand you a
   Vavr-only artifact I hadn't compiled. So the `vavr-variant/` files are careful and
   idiomatic but **unverified — run `mvn compile` before trusting them.**

If you'd rather the whole thing be Vavr-first, say so and I'll flip it; it's mechanical.
The place Vavr helps and the place it doesn't are spelled out in
`vavr-variant/README-vavr.md`. Short version: adopt `Try` + `Seq`; keep the concurrency
(`mapConcurrent` on virtual threads) and the pattern matching (native `switch`) on the JDK.

## JDK floor

Hard requirement: **JDK 24+**, because `Gatherers.mapConcurrent` is only final in 24
(JEP 485; it was preview in 22–23 and absent in 21). If you're pinned below 24, the
`mapConcurrent` fan-out won't compile. Fallbacks: run checks on a
`newVirtualThreadPerTaskExecutor()` with a `Semaphore` for the concurrency cap, or a
`StructuredTaskScope`. The rest of the design is unaffected.

## The timing budget is additive — this is the important one

The "under a minute" target is **not** just the slowest check. The sequential chain runs
first, and only then do the checks fan out:

```
total ≈ fetch + generate + max(checks)
```

Because the checks run concurrently, their contribution is roughly the slowest single
check (plus a little scheduling overhead), not their sum. But that batch sits *after*
fetch + generate, which are sequential and data-dependent — you can't overlap them. So
your real budget is `minute − (fetch + generate)` for the whole check batch. If the
known-slow upstream call eats 25s, you have ~35s for generate + the slowest check. Bound
each external call with `HttpClient`'s per-request `.timeout(...)`; `mapConcurrent` has no
built-in per-task timeout.

## `mapConcurrent` caveats worth remembering

- **No timeout.** Per-request `HttpClient` timeouts are your only bound; without them a
  single stuck check can hold the whole batch open.
- **Fail-fast on exceptions.** If a mapper *throws*, `mapConcurrent` cancels the
  remaining tasks. That's why `Check.evaluate` catches business exceptions and returns a
  `FAIL` **value** — one broken check must not cancel the other 29. The only thing we let
  escape is cancellation (interrupt / `RunCancelled`), which *should* abort the batch.
- **Order preserved.** Results come back in encounter order, so the report is stable
  regardless of which check finished first.

## Cancellation / supersession semantics

`RunRegistry` is latest-wins per pack key: a new run cancels the in-flight one with
`Future.cancel(true)`, which interrupts the run's virtual thread. A subtlety to know:
after `cancel(true)`, `future.get()` throws `CancellationException` **even though** the
task catches the interrupt and returns "empty" — the Future is already in the CANCELLED
state, so its return value is discarded. That's why `RunRegistry.await(...)` treats
`CancellationException` as "superseded -> `Optional.empty()`". A superseded run reports
and persists nothing. This is only safe because **runs are read-only until the very end**
(they assemble a result and evaluate checks; they don't mutate anything), so a cancelled
run needs no compensation/rollback. If you ever add write side effects mid-run, revisit
this.

## Severity

Two levels: `CRITICAL` and `WARNING`. The pack passes iff there are no CRITICAL failures
and nothing was skipped; a WARNING failure yields `passed_with_warnings`. Pipeline stages
are always CRITICAL (nothing downstream can run if fetch/generate fails). This lets a
flaky-but-nonblocking check (e.g. a slow audit endpoint) surface in the report without
failing the release gate. Tune which checks are which — that's a product decision.

## Retries

Retry only **transient, idempotent** failures (connection resets, 502/503/504, timeouts),
with a small bounded backoff, and only where a retry can't double-trigger a side effect.
The report generation step in particular may not be safely retryable if it isn't
idempotent — prefer an idempotency key over blind retries. Retries also eat the timing
budget above, so cap attempts tightly.

## Correlation IDs — use ScopedValue, not ThreadLocal

To stitch logs for one run together, propagate a run/correlation ID. **`ThreadLocal` will
not survive** the hop into the virtual threads `mapConcurrent` spawns per check. Use a
`ScopedValue` bound around the run; it's designed to be inherited by the child virtual
threads, so every check logs under the same run ID. (Structured concurrency scopes
inherit scoped values the same way.)

## Persistence (when you add it)

Two tables mirror the two record types:

- `sanity_run` — one row per (non-superseded) run: id, pack key, trigger
  (scheduled/release/manual), started/finished, status, counts.
- `sanity_step` — one row per `StepResult`: run id (FK), name, kind, severity, status,
  http_status, detail, elapsed_ms.

Tag every run with the **release id / build id** you're validating. The single most
useful thing this buys you later is spotting *load-induced* flakiness — checks that only
fail on high-traffic releases — which you can't see without correlating runs to releases.
Keep **metrics separate** from this audit trail: emit counters/timers to
Micrometer/Prometheus (pass rate, per-check latency, batch wall-time) rather than querying
the runs table for dashboards.

## Jira / notifications

Post **on failure only**, and make it **idempotent** — key the ticket/update by
(pack key, release id) so a re-run or a retry updates the existing ticket instead of
spawning duplicates. Attach the Markdown report (`Reports.toMarkdown`) as the body.

## Scheduling and the /run endpoint (not built here)

- **Scheduler:** prefer `scheduleWithFixedDelay` over `scheduleAtFixedRate` so a slow run
  can't cause runs to pile up back-to-back.
- **Webhooks:** if releases can burst, debounce the trigger — coalesce a flurry of
  release events into one run. The supersession registry already makes a late trigger
  cancel an early one, which is most of what you want.
- **Endpoint:** `/run` should require auth (it can hammer upstreams and write to Jira),
  and should return the compact `Reports.toRunResponse(...)` line while persisting the
  full report asynchronously.

## What's deliberately stubbed

No web server, no real HTTP, no database, no Jira client. `upstream/stub/*` fakes the two
services (with configurable latency and forced-failure status so the demo can drive the
PASS/FAIL/SKIP/supersession scenarios). Everything above the transport boundary —
railway, fan-out, severity folding, cancellation, reporting — is real and is what you'd
keep. Swapping the stubs for `HttpClient` implementations is the main step to production;
the README shows the shape.
