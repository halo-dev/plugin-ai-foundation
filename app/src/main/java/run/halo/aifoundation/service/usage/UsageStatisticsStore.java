package run.halo.aifoundation.service.usage;

import run.halo.aifoundation.service.observation.UsageCallStart;
import run.halo.aifoundation.service.observation.UsageCallTerminal;
import run.halo.aifoundation.service.observation.UsageExecutionRecord;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UsageStatisticsStore extends AutoCloseable {

    /** Execute an ordered group atomically. */
    void writeBatch(List<Runnable> writes);

    void initialize();

    long currentEpoch();

    UsageHealthState readHealth();

    void writeHealth(UsageHealthState health);

    /** Persist health and the call markers already included in its incomplete count atomically. */
    void writeHealth(UsageHealthState health, List<UsageCallStart> incompleteCalls);

    void startCall(UsageCallStart start);

    void recordExecution(UsageExecutionRecord execution);

    void finishCall(UsageCallTerminal terminal);

    UsageSummary summary(UsageQuery query, boolean complete);

    List<UsageTrendPoint> trends(UsageQuery query, boolean complete,
        UsageBreakdownDimension dimension);

    UsageBreakdown breakdown(UsageQuery query, UsageBreakdownDimension dimension);

    UsageCallPage listCalls(UsageQuery query, int size, String cursor);

    Optional<UsageCallDetail> getCall(String id);

    /** Distinct caller plugins across all retained calls, ordered by plugin name. */
    List<UsageCallerPlugin> callerPlugins();

    long reset();

    /** Atomically recover interrupted calls and persist their incomplete health interval. */
    void reconcileAbandoned(Instant now);

    /** Recover lost terminals without recounting failures already recorded by the service. */
    void reconcileAbandoned(Instant now, java.util.Set<String> activeCallIds);

    /**
     * Perform bounded maintenance work; return true to yield and continue in another task.
     */
    boolean retainBatch(Clock clock);

    @Override
    void close();
}
