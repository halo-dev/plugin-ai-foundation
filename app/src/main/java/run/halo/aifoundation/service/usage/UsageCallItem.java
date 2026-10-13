package run.halo.aifoundation.service.usage;

import run.halo.aifoundation.service.observation.NormalizedUsage;
import run.halo.aifoundation.service.observation.UsageStatus;

import java.time.Instant;

public record UsageCallItem(
    String id,
    Instant startedAt,
    Long durationMillis,
    String callerPluginName,
    String callerPluginVersion,
    String feature,
    String operation,
    String modelType,
    String modelName,
    String providerName,
    String requestModelId,
    String responseModelId,
    boolean streaming,
    UsageStatus status,
    String errorType,
    int stepCount,
    int attemptCount,
    int missingExecutionCount,
    boolean complete,
    NormalizedUsage usage
) {
}
