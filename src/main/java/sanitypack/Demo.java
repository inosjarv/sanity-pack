package sanitypack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Runnable demo of a chained API flow:
 *   authenticate -> fetchUser -> fetchOrders -> emailReceipt(optional) -> summarize
 * Each step uses results from earlier steps via the shared context.
 * fetchOrders is deliberately flaky to show retry/backoff in action.
 */
public class Demo {
    private static final Logger logger = LoggerFactory.getLogger(Demo.class);
    
    // Declare context keys as constants for type-safe data passing.
    static final TaskContext.Key<String>  AUTH_TOKEN  = TaskContext.Key.of("authToken");
    static final TaskContext.Key<Integer> USER_ID     = TaskContext.Key.of("userId");
    static final TaskContext.Key<Integer> ORDER_COUNT = TaskContext.Key.of("orderCount");

    private static int orderAttempts = 0; // simulate flakiness in the demo only

    static void main(String[] ignored) {
        StepListener log = new StepListener() {
            public void onStart(String s, int a)  {
                logger.info("-> {}{}", s, a > 1 ? " (attempt " + a + ")" : ""); }
            public void onSuccess(String s, int a) {
                logger.info("   [ok] {}", s); }
            public void onRetry(String s, int a, Throwable e) {
                logger.info("   [retry] {} failed: {}", s, e.getMessage()); }
            public void onSkip(String s)           {
                logger.info("   [skip] {}", s); }
            public void onFailure(String s, int a, Throwable e) {
                logger.error("   [fail] {} gave up after {} attempt(s): ", s, a, e); }
        };

        Task pipeline = Task.named("sanity-pack")
            .listener(log)
            .step("authenticate", ctx ->
                ctx.put(AUTH_TOKEN, "tok_" + System.currentTimeMillis()))
            .step(Step.builder("fetchUser", ctx -> {
                    String token = ctx.get(AUTH_TOKEN);   // uses previous result
                    if (token.isEmpty()) throw new IllegalStateException("no token");
                    ctx.put(USER_ID, 42);
                }).retry(3, Duration.ofMillis(50)).build())
            .step(Step.builder("fetchOrders", ctx -> {
                    int userId = ctx.get(USER_ID);        // uses previous result
                    if (orderAttempts++ < 2) throw new RuntimeException("503 from orders service");
                    ctx.put(ORDER_COUNT, userId == 42 ? 7 : 0);
                }).retry(3, Duration.ofMillis(50)).build())
            .step(Step.builder("emailReceipt", _ -> {
                    throw new RuntimeException("SMTP timeout"); // non-critical
                }).optional().build())
            .step("summarize", ctx ->
                    logger.info("   user={} orders={}", ctx.get(USER_ID), ctx.get(ORDER_COUNT)))
            .build();

        TaskResult result = pipeline.execute();

        logger.info("Status : {}", result.status());
        logger.info("Elapsed: {} ms", result.elapsed().toMillis());
        result.failedStep().ifPresent(s -> logger.info("Failed at: {}", s));

        logger.info("\nStep report:");
        for (TaskResult.StepRecord r : result.steps()) {
            String state = r.skipped ? "SKIPPED" : (r.succeeded ? "PASS" : "FAIL");
            logger.info(String.format("  %-13s %-7s attempts=%d%n", r.name, state, r.attempts));
        }
    }
}
