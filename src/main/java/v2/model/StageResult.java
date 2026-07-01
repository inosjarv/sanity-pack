package v2.model;

/** Result of a pipeline stage: carries the produced value forward on success. */
public record StageResult<R>(boolean ok, R value, int statusCode, String detail) {
    public static <R> StageResult<R> success(R value, int statusCode) {
        return new StageResult<>(true, value, statusCode, "ok");
    }
    public static <R> StageResult<R> failure(int statusCode, String detail) {
        return new StageResult<>(false, null, statusCode, detail);
    }
}
