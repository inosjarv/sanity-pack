package v2.pipeline;

import sanitypack.v2.model.StageResult;

/** A pipeline stage: transforms the previous value into the next; may throw (IO). */
@FunctionalInterface
public interface Stage<T, R> {
    StageResult<R> run(T input) throws Exception;
}
