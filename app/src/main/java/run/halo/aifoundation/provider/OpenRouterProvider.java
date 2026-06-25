package run.halo.aifoundation.provider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.capability.CapabilitySource;
import run.halo.aifoundation.capability.ImageGenerationCapability;
import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.capability.ModelCapabilitySources;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderImageGenerationClient;
import run.halo.aifoundation.provider.support.ProviderRerankingClient;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;
import run.halo.aifoundation.provider.support.RerankingModelProviderOptions;
import run.halo.aifoundation.provider.support.image.ImageGenerationClientOptions;
import run.halo.aifoundation.provider.support.image.OpenRouterImageGenerationClient;
import run.halo.aifoundation.provider.support.openai.OpenAiEmbeddingOptionsFactory;
import run.halo.aifoundation.provider.support.openai.OpenAiReasoningOptions;
import run.halo.aifoundation.provider.support.rerank.StandardRerankingClient;

@Component
public class OpenRouterProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "https://openrouter.ai/api/v1";
    private static final String COMPLETIONS_PATH = "/chat/completions";
    private static final String EMBEDDINGS_PATH = "/embeddings";

    @Override
    public String getProviderType() {
        return "openrouter";
    }

    @Override
    public String getDisplayName() {
        return "OpenRouter";
    }

    @Override
    public String getDescription() {
        return "OpenRouter 是一个统一的 AI 模型接口平台，聚合了多种开源和商业大模型。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/openrouter.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://openrouter.ai";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://openrouter.ai/docs";
    }

    @Override
    public boolean isBuiltIn() {
        return true;
    }

    @Override
    public boolean requiresBaseUrl() {
        return false;
    }

    @Override
    public String getDefaultBaseUrl() {
        return DEFAULT_BASE_URL;
    }

    @Override
    public List<AdapterType> getSupportedAdapterTypes() {
        return List.of(AdapterType.OPENAI_CHAT, AdapterType.OPENAI_EMBEDDING, AdapterType.RERANK,
            AdapterType.OPENROUTER_IMAGE);
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey, String modelId) {
        return buildOpenAiCompatibleChatModel(provider, apiKey, modelId);
    }

    @Override
    public EmbeddingModel buildEmbeddingModel(AiProvider provider, String apiKey, String modelId) {
        return buildOpenAiCompatibleEmbeddingModel(provider, apiKey, modelId);
    }

    @Override
    public ProviderRerankingClient buildRerankingClient(AiProvider provider, String apiKey,
        String modelId) {
        return new StandardRerankingClient(getProviderType(), trimTrailingSlash(resolveBaseUrl(provider)),
            "/rerank", modelId, apiKey, webClientBuilder(provider));
    }

    @Override
    public ProviderImageGenerationClient buildImageGenerationClient(AiProvider provider,
        String apiKey, String modelId) {
        return new OpenRouterImageGenerationClient(new ImageGenerationClientOptions(
            getProviderType(), resolveBaseUrl(provider), apiKey, modelId, null),
            webClientBuilder(provider));
    }

    @Override
    public Mono<List<DiscoveredModel>> discoverModels(AiProvider provider, String apiKey) {
        return Mono.zip(discoverOpenAiCompatibleModels(provider, apiKey),
            discoverImageModels(provider, apiKey))
            .map(tuple -> {
                var models = new LinkedHashMap<String, DiscoveredModel>();
                tuple.getT1().forEach(model -> models.put(model.modelId(), model));
                tuple.getT2().forEach(model -> models.put(model.modelId(), model));
                return List.copyOf(models.values());
            });
    }

    private Mono<List<DiscoveredModel>> discoverImageModels(AiProvider provider, String apiKey) {
        return getDiscoveryJson(provider, apiKey,
            uriBuilder -> uriBuilder.path("/images/models").build(),
            this::customizeDiscoveryRequest
        ).map(json -> {
            var data = listValue(json, "data");
            if (data == null) {
                return List.<DiscoveredModel>of();
            }
            return discoveredModelsFromNodes(data, "id", this::toImageGenerationModel);
        });
    }

    private DiscoveredModel toImageGenerationModel(java.util.Map<?, ?> node) {
        var modelId = stringValue(node, "id");
        if (modelId.isBlank()) {
            return null;
        }
        var architecture = node.get("architecture");
        var imageToImage = architecture instanceof java.util.Map<?, ?> architectureMap
            && containsToken(architectureMap.get("input_modalities"), "image");
        return new DiscoveredModel(
            modelId,
            stringValue(node, "name"),
            ModelType.IMAGE_GENERATION,
            Set.of(),
            AdapterType.OPENROUTER_IMAGE,
            DiscoverySource.REMOTE,
            DiscoveryConfidence.HIGH,
            ModelCapabilities.builder()
                .imageGeneration(ImageGenerationCapability.builder()
                    .textToImage(true)
                    .imageToImage(imageToImage ? true : null)
                    .maxImagesPerCall(1)
                    .build())
                .build(),
            ModelCapabilitySources.builder()
                .imageGeneration(CapabilitySource.REMOTE)
                .build()
        );
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions() {
        var reasoningControlOptions =
            ReasoningControlOptions.openAiCompatibleEffort(OpenAiReasoningOptions::applyEffort);
        return openAiCompatibleLanguageModelProviderOptions(reasoningControlOptions, null, true);
    }

    @Override
    public EmbeddingModelProviderOptions embeddingModelProviderOptions() {
        return new EmbeddingModelProviderOptions("openai", OpenAiEmbeddingOptionsFactory::build);
    }

    @Override
    public RerankingModelProviderOptions rerankingModelProviderOptions() {
        return RerankingModelProviderOptions.builder()
            .providerOptionsSupported(true)
            .build();
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank() || !value.endsWith("/")) {
            return value;
        }
        return value.substring(0, value.length() - 1);
    }
}
