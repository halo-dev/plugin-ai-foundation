package run.halo.aifoundation.service.usage;

import java.time.Instant;

public record UsageCallItem(
    String id,
    Instant startedAt,
    Instant completedAt,
    Long durationMillis,
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
    String responseModelId,
    boolean streaming,
    UsageStatus status,
    String errorType,
    String errorCode,
    int stepCount,
    int attemptCount,
    int missingExecutionCount,
    boolean complete,
    NormalizedUsage usage
) {
}
