package v2.upstream.stub;

import sanitypack.v2.upstream.*;

/** Configurable stub report service. */
public final class StubReportService implements ReportService {
    private final int forcedStatus;
    public StubReportService(int forcedStatus) { this.forcedStatus = forcedStatus; }
    public static StubReportService healthy()           { return new StubReportService(200); }
    public static StubReportService failing(int status) { return new StubReportService(status); }

    @Override public Resp generate(String upstreamPayload) {
        if (forcedStatus != 200) return new Resp(forcedStatus, "report service error");
        return new Resp(200, "reportId=RPT-1;revenue=1000;rows=alpha,beta,gamma");
    }
}
