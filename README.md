# sanity-pack — a tiny task/subtask pipeline engine

Zero-dependency Java (21) engine for firing API calls **step by step**, where
each call can use results produced by earlier calls.

## The one design decision that matters

There are two ways to pass data down a chain:

1. **Typed chain** — `Step<A,B>`, `Step<B,C>`, ... The compiler guarantees
   wiring, but it breaks the moment a step needs output from *two* earlier
   steps (very common with real APIs). Rigid.
2. **Shared context (chosen here)** — every step reads/writes a `TaskContext`.
   A step can use results from *any* earlier step, not just the previous one.
   Trade-off: a missing value is caught at runtime, not compile time — so the
   context fails loudly with a clear message ("Missing value for key 'token'...").

For sequential API orchestration the shared-context model wins. Type-safe `Key`s
recover most of the safety the typed chain would have given you.

## Run it

```bash
javac sanitypack/*.java
java sanitypack.Demo
```

## Shape of the API

```java
static final TaskContext.Key<String> TOKEN = TaskContext.Key.of("token");

Task pipeline = Task.named("sanity-pack")
    .listener(myLogger)                              // observability
    .step("authenticate", ctx -> ctx.put(TOKEN, login()))
    .step(Step.builder("fetchUser", ctx -> {
            var user = api.getUser(ctx.get(TOKEN));   // uses earlier result
            ctx.put(USER, user);
        }).retry(3, Duration.ofMillis(200)).build())  // retry w/ backoff
    .step(Step.builder("notify", ctx -> sendEmail(ctx.get(USER)))
            .optional().build())                       // failure is non-fatal
    .build();

TaskResult result = pipeline.execute();
if (!result.isSuccess()) { /* result.failedStep(), result.error() */ }
```

Per-step knobs: `.retry(n)`, `.retry(n, backoff)`, `.optional()`,
`.runIf(predicate)`.

## A real HTTP step (no extra deps, java.net.http)

```java
static final TaskContext.Key<String> TOKEN = TaskContext.Key.of("token");
static final HttpClient HTTP = HttpClient.newHttpClient();

Step fetchProfile = Step.builder("fetchProfile", ctx -> {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("https://api.example.com/me"))
            .header("Authorization", "Bearer " + ctx.get(TOKEN))
            .GET()
            .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 400) {
            throw new IOException("GET /me -> " + res.statusCode());
        }
        ctx.put(PROFILE_JSON, res.body());
    })
    .retry(3, Duration.ofMillis(200))
    .build();
```

Add **Jackson** (`com.fasterxml.jackson.core:jackson-databind`) only when you
want to parse JSON into objects — that's the one library worth pulling in.

## Subtask hierarchy ("so on and so forth")

A `Task` can be nested inside another `Task`; the child shares the same
context, so data still flows across the boundary:

```java
Task setup   = Task.named("setup").step(...).step(...).build();
Task checks  = Task.named("checks").step(...).step(...).build();

Task full = Task.named("full-run")
    .subTask(setup)
    .subTask(checks)
    .build();
```

## When to outgrow this

This design is ideal for **sequential** flows. Reach for a real workflow engine
(e.g. Temporal) only if you later need: durable state that survives process
restarts / resume-from-failure, long-running flows (hours/days), or
human-in-the-loop waits. Parallel fan-out is a small extension (run independent
steps on an ExecutorService, then join) — easy to add when you need it.

## Files

| File | Responsibility |
|------|----------------|
| `TaskContext.java`  | Type-safe shared state passed between steps |
| `Subtask.java`      | The unit-of-work interface (your API call) |
| `Step.java`         | Subtask + policy (retry / optional / conditional) |
| `Task.java`         | The engine: runs steps in order, handles retries, nesting |
| `TaskResult.java`   | Outcome + per-step pass/fail report |
| `StepListener.java` | Logging / metrics hook |
| `Demo.java`         | Runnable example |
