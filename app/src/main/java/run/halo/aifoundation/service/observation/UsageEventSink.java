package run.halo.aifoundation.service.observation;

/** Nonblocking consumer of safe, immutable execution facts. Implementations must not throw. */
public interface UsageEventSink {
    void recordExecution(UsageCallSession session, UsageExecutionRecord execution);

    void finishCall(UsageCallSession session, UsageCallTerminal terminal);
}
