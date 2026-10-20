package run.halo.aifoundation.provider.tokenhub;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
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
import run.halo.aifoundation.provider.support.ProviderFeatureSets;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;
import run.halo.aifoundation.provider.support.StructuredOutputSupport;

/** Tencent Cloud TokenHub integration backed by its documented OpenAI-compatible APIs. */
@Component
public class TokenHubProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "https://tokenhub.tencentmaas.com/v1";

    @Override
    public String getProviderType() {
        return "tokenhub";
    }

    @Override
    public String getDisplayName() {
        return "腾讯云 TokenHub";
    }

    @Override
    public String getDescription() {
        return "腾讯云大模型服务平台，统一接入混元及第三方对话与文本向量模型。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/tokenhub.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://cloud.tencent.com/product/tokenhub";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://cloud.tencent.com/document/product/1823/130079";
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
        return List.of(AdapterType.TOKENHUB_CHAT, AdapterType.TOKENHUB_EMBEDDING);
    }

    @Override
    public List<ModelFeature> getSupportedFeatures() {
        // These are adapter-level capabilities; the platform documents that support varies by
        // model, so administrators narrow them on the model itself.
        return ProviderFeatureSets.VISION_REASONING;
    }

    @Override
    protected String defaultReasoningMappingTemplate() {
        return "reasoning.tokenhub";
    }

    @Override
    protected String defaultReasoningMappingTemplate(AdapterType adapterType) {
        return adapterType == AdapterType.TOKENHUB_CHAT ? "reasoning.tokenhub" : null;
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey, String modelId) {
        return new TokenHubChatModel(chatCompletionsOptions(provider, apiKey, modelId, Map.of()),
            webClientBuilder(provider));
    }

    @Override
    public EmbeddingModel buildEmbeddingModel(AiProvider provider, String apiKey, String modelId) {
        var options = TokenHubEmbeddingOptions.builder()
            .baseUrl(resolveBaseUrl(provider))
            .apiKey(apiKey)
            .model(modelId)
            .build();
        return new TokenHubEmbeddingModel(options, webClientBuilder(provider));
    }

    @Override
    public EmbeddingModelProviderOptions embeddingModelProviderOptions() {
        return new EmbeddingModelProviderOptions(TokenHubEmbeddingOptionsFactory::build);
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions() {
        var reasoning = ReasoningControlOptions.unsupported();
        var optionsFactory = chatCompletionsOptionsFactory(
            reasoning, false, StructuredOutputSupport.JSON_SCHEMA);
        return chatCompletionsProviderOptionsBuilder()
            .reasoningHistorySupported(true)
            .streamToolCallsForReasoning(true)
            .requestHeadersSupported(true)
            .seedSupported(true)
            .nativeStrictToolSchemas(false)
            .structuredOutputSupport(StructuredOutputSupport.JSON_SCHEMA)
            .chatOptionsFactory(optionsFactory::basic)
            .toolCallingChatOptionsFactory(optionsFactory::toolCalling)
            .structuredOutputChatOptionsFactory(optionsFactory::structured)
            .reasoningControlOptions(reasoning)
            .reasoningContentExtractor(this::reasoningContent)
            .build();
    }

    @Override
    public Mono<List<DiscoveredModel>> discoverModels(AiProvider provider, String apiKey) {
        // TokenHub returns identifiers, display names and status only, without model types.
        // Classify every entry as the provider default and let administrators adjust the type
        // before importing; never infer types from identifier text.
        return discoverIdentifierOnlyModels(provider, apiKey, "/models");
    }

    @Override
    public Map<ModelParameter, DefaultParameterMapping> getDefaultParameterMappings() {
        var defaults = new EnumMap<ModelParameter, DefaultParameterMapping>(ModelParameter.class);
        defaults.putAll(super.getDefaultParameterMappings());
        defaults.put(ModelParameter.MAX_OUTPUT_TOKENS,
            DefaultParameterMapping.template("openai.max-completion-tokens"));
        // TokenHub documents that top_k, min_p and repetition_penalty are ignored, and that
        // embedding dimensions are fixed by the model.
        for (var parameter : List.of(ModelParameter.TOP_K, ModelParameter.MIN_P,
            ModelParameter.REPETITION_PENALTY, ModelParameter.DIMENSIONS)) {
            defaults.put(parameter, DefaultParameterMapping.unsupported());
        }
        return Map.copyOf(defaults);
    }

    private String reasoningContent(AssistantMessage message) {
        if (message == null || message.getMetadata() == null) {
            return null;
        }
        for (var key : List.of("reasoningContent", "reasoning_content", "reasoning")) {
            var value = message.getMetadata().get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }
}
