package run.halo.aifoundation.service.usage;

import run.halo.aifoundation.service.observation.UsageCallStart;
import run.halo.aifoundation.service.observation.UsageCallTerminal;
import run.halo.aifoundation.service.observation.UsageExecutionRecord;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UsageStatisticsStore extends AutoCloseable {

    /** Execute an ordered group atomically where the storage engine supports transactions. */
    default void writeBatch(List<Runnable> writes) {
        writes.forEach(Runnable::run);
    }

    void initialize();

    long currentEpoch();

    UsageHealthState readHealth();

    void writeHealth(UsageHealthState health);

    void startCall(UsageCallStart start);

    void recordExecution(UsageExecutionRecord execution);

    void finishCall(UsageCallTerminal terminal);

    UsageSummary summary(UsageQuery query, boolean complete);

    List<UsageTrendPoint> trends(UsageQuery query, boolean complete,
        UsageBreakdownDimension dimension);

    UsageBreakdown breakdown(UsageQuery query, UsageBreakdownDimension dimension);

    UsageCallPage listCalls(UsageQuery query, int size, String cursor);

    Optional<UsageCallDetail> getCall(String id);

    long reset();

    void reconcileAbandoned(Instant now);

    /**
     * Perform bounded maintenance work; return true to yield and continue in another task.
     */
    boolean rollupAndRetainBatch(Clock clock);

    void backup();

    @Override
    void close();
}
