package run.halo.aifoundation.provider;

import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.extension.AiProvider;

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

    List<String> getSupportedEndpointTypes();

    boolean supportsEmbeddings();

    // ── Behavior ──────────────────────────────────────────────

    ChatModel buildChatModel(AiProvider provider, String apiKey, String modelId);

    @Nullable
    default EmbeddingModel buildEmbeddingModel(AiProvider provider, String apiKey, String modelId) {
        return null;
    }

    Mono<List<DiscoveredModel>> discoverModels(AiProvider provider, String apiKey);

    default int maxEmbeddingsPerCall() {
        return 96;
    }

    default boolean supportsParallelCalls() {
        return true;
    }
}
