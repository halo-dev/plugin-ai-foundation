package run.halo.aifoundation.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;

public interface AiProviderType {

    // ── Identity ──────────────────────────────────────────────

    String getProviderType();

    // ── Display metadata ──────────────────────────────────────

    String getDisplayName();

    @Nullable
    default String getDescription() {
        return null;
    }

    @Nullable
    default String getIconUrl() {
        return null;
    }

    @Nullable
    default String getDocumentationUrl() {
        return null;
    }

    @Nullable
    default String getWebsiteUrl() {
        return null;
    }

    // ── Configuration metadata ────────────────────────────────

    boolean isBuiltIn();

    boolean requiresBaseUrl();

    @Nullable
    String getDefaultBaseUrl();

    List<AdapterType> getSupportedAdapterTypes();

    default List<ModelType> getSupportedModelTypes() {
        var modelTypes = new ArrayList<ModelType>();
        for (var adapterType : getSupportedAdapterTypes()) {
            if (!modelTypes.contains(adapterType.getModelType())) {
                modelTypes.add(adapterType.getModelType());
            }
        }
        return List.copyOf(modelTypes);
    }

    default List<ModelFeature> getSupportedFeatures() {
        if (!getSupportedModelTypes().contains(ModelType.LANGUAGE)) {
            return List.of();
        }
        return List.of(
            ModelFeature.STREAMING,
            ModelFeature.VISION,
            ModelFeature.TOOL_CALL,
            ModelFeature.STRUCTURED_OUTPUT,
            ModelFeature.REASONING
        );
    }

    // ── Behavior ──────────────────────────────────────────────

    ChatModel buildChatModel(AiProvider provider, String apiKey, String modelId);

    default LanguageModelProviderOptions languageModelProviderOptions() {
        return LanguageModelProviderOptions.defaults();
    }

    default EmbeddingModelProviderOptions embeddingModelProviderOptions() {
        return EmbeddingModelProviderOptions.defaults(getProviderType());
    }

    @Nullable
    default EmbeddingModel buildEmbeddingModel(AiProvider provider, String apiKey, String modelId) {
        return null;
    }

    Mono<List<DiscoveredModel>> discoverModels(AiProvider provider, String apiKey);

    default Optional<AdapterType> recommendAdapterType(DiscoveredModel model) {
        return recommendAdapterType(model.modelType());
    }

    default Optional<AdapterType> recommendAdapterType(ModelType modelType) {
        return AdapterType.firstFor(getSupportedAdapterTypes(), modelType);
    }

    default int maxEmbeddingsPerCall() {
        return 96;
    }

    default boolean supportsParallelCalls() {
        return true;
    }
}
