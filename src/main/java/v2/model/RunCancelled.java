package v2.model;

/**
 * Signals that a run was superseded/cancelled and its results must be discarded.
 * Lives in the shared 'model' package so both the pipeline and checks can throw it
 * without creating a dependency cycle.
 */
public final class RunCancelled extends RuntimeException {
    public RunCancelled() { super("run cancelled / superseded"); }
}
