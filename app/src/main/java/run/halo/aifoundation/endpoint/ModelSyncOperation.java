package run.halo.aifoundation.endpoint;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;
import run.halo.aifoundation.extension.AiModel;

/**
 * One batch sync operation.
 *
 * @param action sync action
 * @param modelId provider-side model id used for matching and reporting
 * @param localModelName saved {@code AiModel.metadata.name} for update, enable, and disable actions
 * @param model complete model payload for create actions
 * @param discoveredModel remote discovery item whose discovery-owned profile is applied by update actions
 */
@Schema(description = "A single model sync operation.")
public record ModelSyncOperation(
        ModelSyncAction action,
        @Nullable String modelId,
        @Nullable String localModelName,
        @Nullable AiModel model,
        @Nullable DiscoveredModelItem discoveredModel) {}
