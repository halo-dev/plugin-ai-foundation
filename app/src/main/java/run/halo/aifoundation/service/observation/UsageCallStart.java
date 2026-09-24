package run.halo.aifoundation.service.observation;

import java.time.Instant;

public record UsageCallStart(
    String id,
    long epoch,
    Instant startedAt,
    String callerPluginName,
    String callerPluginVersion,
    String callerDetectionSource,
    String feature,
    String operation,
    String modelType,
    String modelName,
    String providerName,
    String providerType,
    String requestModelId,
    boolean streaming
) {
}
