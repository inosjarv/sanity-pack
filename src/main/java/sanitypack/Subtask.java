package sanitypack;

/**
 * One unit of work. This is where an actual API call goes.
 * Read inputs from the context, do the work, write results back.
 * Throw on failure - the engine handles retries / failure policy centrally.
 */
@FunctionalInterface
public interface Subtask {
    void run(TaskContext context) throws Exception;
}
