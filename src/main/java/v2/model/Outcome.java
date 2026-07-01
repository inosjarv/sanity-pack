package v2.model;

/** Raw result of evaluating a check body, before timing/metadata is attached. */
public record Outcome(boolean passed, int statusCode, String detail) {
    public static Outcome pass(String detail)                 { return new Outcome(true, -1, detail); }
    public static Outcome pass(int statusCode)                { return new Outcome(true, statusCode, "ok"); }
    public static Outcome fail(String detail)                 { return new Outcome(false, -1, detail); }
    public static Outcome fail(int statusCode, String detail) { return new Outcome(false, statusCode, detail); }
}
