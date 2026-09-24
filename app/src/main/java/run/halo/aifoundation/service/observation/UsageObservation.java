package run.halo.aifoundation.service.observation;

import java.util.Map;
import run.halo.aifoundation.service.audit.ModelCallContext;

/** Subscription-scoped observation. No persistence or query API is visible to model wrappers. */
public interface UsageObservation {
    UsageCallDescriptor describeCall(ModelCallContext context, String operation,
        boolean streaming, Map<String, Object> metadata);

    UsageCallSession beginCall(UsageCallDescriptor descriptor);
}
