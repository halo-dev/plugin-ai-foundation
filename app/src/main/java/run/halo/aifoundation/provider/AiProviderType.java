package run.halo.aifoundation.provider;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.ModelCapability;

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

    default Optional<String> recommendEndpointType(DiscoveredModel model) {
        return recommendEndpointType(model.modelId(), model.capabilities());
    }

    default Optional<String> recommendEndpointType(String modelId,
        Collection<ModelCapability> capabilities) {
        var supportedTypes = getSupportedEndpointTypes();
        if (supportedTypes == null || supportedTypes.isEmpty()) {
            return Optional.empty();
        }

        var normalizedCapabilities = new LinkedHashSet<>(
            capabilities != null ? capabilities : Set.<ModelCapability>of());
        if (normalizedCapabilities.isEmpty()) {
            var normalizedModelId = modelId != null ? modelId.toLowerCase(Locale.ROOT) : "";
            normalizedCapabilities.add(normalizedModelId.contains("embed")
                ? ModelCapability.EMBEDDING : ModelCapability.CHAT);
        }

        if (normalizedCapabilities.contains(ModelCapability.EMBEDDING)) {
            var embeddingEndpoint = findSupportedEndpointType("embedding");
            if (embeddingEndpoint.isPresent()) {
                return embeddingEndpoint;
            }
        }
        if (normalizedCapabilities.contains(ModelCapability.CHAT)) {
            var chatEndpoint = findSupportedEndpointType("chat");
            if (chatEndpoint.isPresent()) {
                return chatEndpoint;
            }
        }
        return Optional.empty();
    }

    private Optional<String> findSupportedEndpointType(String token) {
        return getSupportedEndpointTypes().stream()
            .filter(endpointType -> endpointType.toLowerCase(Locale.ROOT).contains(token))
            .findFirst();
    }

    default int maxEmbeddingsPerCall() {
        return 96;
    }

    default boolean supportsParallelCalls() {
        return true;
    }
}
