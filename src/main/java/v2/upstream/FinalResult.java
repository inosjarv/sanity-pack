package v2.upstream;

import java.util.List;

/** The assembled result the sanity checks run against. */
public record FinalResult(String reportId, double revenue, List<String> rows) {}
