package v2.upstream;

/** Generates a report from the upstream data. In production this wraps an HttpClient call. */
public interface ReportService {
    Resp generate(String upstreamPayload) throws Exception;
}
