package run.halo.aifoundation.service.usage;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UsageStatisticsStore extends AutoCloseable {

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

    void backup();

    @Override
    void close();
}
