package v2.upstream.stub;

import sanitypack.v2.upstream.*;

import java.time.Duration;

/** Configurable stub: optional latency (interruptible, to demo cancellation) + optional forced failure. */
public final class StubUpstreamService implements UpstreamService {
    private final Duration latency;
    private final int forcedStatus;   // 200 = healthy

    public StubUpstreamService(Duration latency, int forcedStatus) {
        this.latency = latency;
        this.forcedStatus = forcedStatus;
    }
    public static StubUpstreamService healthy()            { return new StubUpstreamService(Duration.ZERO, 200); }
    public static StubUpstreamService slow(Duration d)     { return new StubUpstreamService(d, 200); }
    public static StubUpstreamService failing(int status)  { return new StubUpstreamService(Duration.ZERO, status); }

    @Override public Resp fetch() throws InterruptedException {
        if (!latency.isZero()) Thread.sleep(latency.toMillis());   // interruptible -> supports cancellation
        if (forcedStatus != 200) return new Resp(forcedStatus, "upstream error");
        return new Resp(200, "revenue=1000;rows=alpha,beta,gamma");
    }
}
