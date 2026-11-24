package run.halo.aifoundation.endpoint;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

@Schema(description = "Result of one model sync operation.")
public record ModelSyncResult(
        @Nullable String modelId,
        @Nullable String modelName,
        ModelSyncAction action,
        ModelSyncOutcome outcome,
        @Nullable String message) {}
