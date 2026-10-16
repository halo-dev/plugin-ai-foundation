package run.halo.aifoundation.service.observation;

import java.util.Map;
import run.halo.aifoundation.service.audit.ModelCallContext;

/**
 * Subscription-scoped observation. No persistence or query API is visible to model wrappers.
 * Implementations must not throw from the event methods.
 */
public interface UsageObservation {

    UsageCallDescriptor describeCall(ModelCallContext context, String operation,
        boolean streaming, Map<String, Object> metadata);

    UsageCallSession beginCall(UsageCallDescriptor descriptor);

    void recordExecution(UsageCallSession session, UsageExecutionRecord execution);

    void finishCall(UsageCallSession session, UsageCallTerminal terminal);
}
