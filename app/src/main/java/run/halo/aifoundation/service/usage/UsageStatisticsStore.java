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

    List<UsageTrendPoint> trends(UsageQuery query, boolean complete);

    UsageCallPage listCalls(UsageQuery query, int size, String cursor);

    Optional<UsageCallDetail> getCall(String id);

    long reset();

    void reconcileAbandoned(Instant now);

    void rollupAndRetain(Clock clock);

    /**
     * Perform bounded maintenance work; return true to yield and continue in another task.
     * The default preserves legacy stores; stores used by the service should override it
     * to bound each transaction instead of retaining the entire dataset in one task.
     */
    default boolean rollupAndRetainBatch(Clock clock) {
        rollupAndRetain(clock);
        return false;
    }

    void backup();

    @Override
    void close();
}
