package sanitypack;

/**
 * Observability hook. Plug in logging, metrics, or tracing here without
 * touching your step logic. Swap NOOP for an SLF4J-backed implementation
 * in production.
 */
public interface StepListener {
    default void onStart(String step, int attempt) {}
    default void onSuccess(String step, int attempts) {}
    default void onRetry(String step, int attempt, Throwable error) {}
    default void onSkip(String step) {}
    default void onFailure(String step, int attempts, Throwable error) {}

    StepListener NOOP = new StepListener() {};
}
