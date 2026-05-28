package run.halo.aifoundation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Summary of a configured AI model resource visible to SDK callers.
 *
 * <p>{@link #getName()} is the value callers pass to {@code AiModelService.languageModel(name)} or
 * {@code AiModelService.embeddingModel(name)}. It is the Halo {@code AiModel.metadata.name}, not the
 * provider's raw model id.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelInfo {
    /** The AiModel.metadata.name (model resource name). */
    private String name;
    /** The AiProvider.metadata.name (provider instance resource name). */
    private String providerName;
    /**
     * Raw model id configured for the provider adapter.
     */
    private String modelId;
    /**
     * Display name configured for console and discovery output.
     */
    private String displayName;
    /**
     * Whether the model is enabled and available for SDK calls.
     */
    private boolean enabled;
}
