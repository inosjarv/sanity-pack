I'm building "sanity-pack": a zero-dependency Java 21 engine that fires API
calls step by step, where each step uses results from earlier ones. It's a
task/subtask pipeline. The code already exists in the sanitypack/ folder.

Design already implemented:
- TaskContext: type-safe shared state (typed Keys) passed between steps —
  chosen over a Step<A,B> typed chain so any step can use ANY earlier result.
- Subtask: functional interface, one unit of work (the API call).
- Step: wraps a Subtask with policy — retry(n, backoff), optional(), runIf().
- Task: engine that runs Steps in order, handles retry/backoff, and can nest
  a Task as a subtask (shares the same context).
- TaskResult: outcome + per-step pass/fail report. StepListener: logging hook.
- Demo: runnable (authenticate → fetchUser → fetchOrders[flaky,retried] →
  emailReceipt[optional] → summarize).

Compile/run:  javac sanitypack/*.java && java sanitypack.Demo

Next tasks:
1. Refactor the hand-rolled retry/backoff in Step/Task to delegate to Failsafe
   (composable RetryPolicy + Timeout + CircuitBreaker), keeping the same
   builder API.
2. Decide whether to add durability/resume; if yes, evaluate Temporal.

Read the files first, then propose the Failsafe refactor.