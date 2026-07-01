package v2.upstream;

/** Fetches the source data. In production this wraps an HttpClient call. */
public interface UpstreamService {
    Resp fetch() throws Exception;
}
