package run.halo.aifoundation.provider.openai;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.AbstractAiProviderType;
import run.halo.aifoundation.provider.mapping.DefaultParameterMapping;
import run.halo.aifoundation.provider.mapping.ModelParameter;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ProviderImageGenerationClient;
import run.halo.aifoundation.provider.support.ProviderModelRef;
import run.halo.aifoundation.provider.support.ProviderFeatureSets;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;
import run.halo.aifoundation.provider.support.image.ImageGenerationClientOptions;

@Component
public class OpenAiProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String COMPLETIONS_PATH = "/chat/completions";
    private static final String EMBEDDINGS_PATH = "/embeddings";

    @Override
    public String getProviderType() {
        return "openai";
    }

    @Override
    public String getDisplayName() {
        return "OpenAI";
    }

    @Override
    public String getDescription() {
        return "OpenAI 原生 Responses、Chat、嵌入与图像服务。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/openai.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://openai.com";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://platform.openai.com/docs";
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
        return List.of(AdapterType.OPENAI_RESPONSES, AdapterType.OPENAI_CHAT,
            AdapterType.OPENAI_EMBEDDING,
            AdapterType.OPENAI_IMAGE);
    }

    @Override
    public List<ModelFeature> getSupportedFeatures() {
        return ProviderFeatureSets.ALL;
    }

    @Override
    protected String defaultReasoningMappingTemplate() {
        return "reasoning.responses-effort";
    }

    @Override
    protected String defaultReasoningMappingTemplate(AdapterType adapterType) {
        if (adapterType == AdapterType.OPENAI_CHAT) {
            return "reasoning.effort";
        }
        if (adapterType == AdapterType.OPENAI_RESPONSES) {
            return "reasoning.responses-effort";
        }
        return null;
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey, String modelId) {
        return new OpenAiResponsesModel(chatCompletionsOptions(provider, apiKey, modelId, Map.of()),
            webClientBuilder(provider));
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey, ProviderModelRef model) {
        var resolved = resolveModel(model);
        var options = chatCompletionsOptions(provider, apiKey, resolved.modelId(), Map.of());
        return switch (resolved.adapterType()) {
            case OPENAI_RESPONSES -> new OpenAiResponsesModel(options,
                webClientBuilder(provider));
            case OPENAI_CHAT -> new OpenAiChatModel(options, webClientBuilder(provider));
            default -> throw new IllegalArgumentException("Unsupported OpenAI language adapter: "
                + resolved.adapterType());
        };
    }

    @Override
    public EmbeddingModel buildEmbeddingModel(AiProvider provider, String apiKey, String modelId) {
        return new OpenAiEmbeddingModel(new OpenAiEmbeddingOptions(resolveBaseUrl(provider),
            apiKey, modelId, null, null, null, Map.of(), Map.of(), null),
            webClientBuilder(provider));
    }

    @Override
    public ProviderImageGenerationClient buildImageGenerationClient(AiProvider provider,
        String apiKey, String modelId) {
        return new OpenAiImageGenerationClient(new ImageGenerationClientOptions(
            getProviderType(), resolveBaseUrl(provider), apiKey, modelId, Map.of()),
            webClientBuilder(provider));
    }

    @Override
    public Mono<List<DiscoveredModel>> discoverModels(AiProvider provider, String apiKey) {
        return discoverIdentifierOnlyModels(provider, apiKey, "/models");
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions() {
        var reasoningControlOptions = ReasoningControlOptions.unsupported();
        return chatCompletionsLanguageModelProviderOptions(reasoningControlOptions, null, true);
    }

    @Override
    public EmbeddingModelProviderOptions embeddingModelProviderOptions() {
        return new EmbeddingModelProviderOptions("openai", OpenAiEmbeddingOptionsFactory::build);
    }

    @Override
    public Map<ModelParameter, DefaultParameterMapping> getDefaultParameterMappings() {
        var defaults = new EnumMap<ModelParameter, DefaultParameterMapping>(ModelParameter.class);
        defaults.putAll(super.getDefaultParameterMappings());
        defaults.put(ModelParameter.MIN_P, DefaultParameterMapping.unsupported());
        defaults.put(ModelParameter.REPETITION_PENALTY, DefaultParameterMapping.unsupported());
        return Map.copyOf(defaults);
    }
}
