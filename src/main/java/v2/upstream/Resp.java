package v2.upstream;

/** Minimal stand-in for an HTTP response (status + body). Real code uses HttpResponse&lt;String&gt;. */
public record Resp(int status, String body) {}
