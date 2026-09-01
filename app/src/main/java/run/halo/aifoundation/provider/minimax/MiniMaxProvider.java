package run.halo.aifoundation.provider.minimax;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.AbstractAiProviderType;
import run.halo.aifoundation.provider.mapping.DefaultParameterMapping;
import run.halo.aifoundation.provider.mapping.ModelParameter;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderFeatureSets;
import run.halo.aifoundation.provider.support.ProviderImageGenerationClient;
import run.halo.aifoundation.provider.support.ProviderModelRef;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;
import run.halo.aifoundation.provider.support.StructuredOutputSupport;
import run.halo.aifoundation.provider.support.image.ImageGenerationClientOptions;

/**
 * MiniMax provider with the recommended Messages API as its default language transport.
 *
 * <p>The OpenAI-compatible Chat surface remains selectable per model, while image generation uses
 * MiniMax's native endpoint. The documented OpenAI-compatible model catalog is treated as a
 * language-model catalog; model identifiers are never inspected to infer a domain or capability.
 */
@Component
public class MiniMaxProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "https://api.minimax.io";

    @Override
    public String getProviderType() {
        return "minimax";
    }

    @Override
    public String getDisplayName() {
        return "MiniMax";
    }

    @Override
    public String getDescription() {
        return "MiniMax 原生模型服务，支持 Messages、Chat Completions、Responses、图像生成、交错思考和显式提示词缓存。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/minimax.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://www.minimax.io";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://platform.minimax.io/docs/api-reference/api-overview";
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
        return List.of(AdapterType.MINIMAX_MESSAGES, AdapterType.MINIMAX_CHAT,
            AdapterType.MINIMAX_RESPONSES, AdapterType.MINIMAX_IMAGE);
    }

    @Override
    public List<ModelFeature> getSupportedFeatures() {
        return ProviderFeatureSets.VISION_REASONING;
    }

    @Override
    public List<ModelFeature> getSupportedFeatures(AdapterType adapterType) {
        return switch (adapterType) {
            case MINIMAX_RESPONSES, MINIMAX_MESSAGES, MINIMAX_CHAT ->
                ProviderFeatureSets.VISION_REASONING;
            default -> List.of();
        };
    }

    @Override
    protected String defaultReasoningMappingTemplate() {
        return "reasoning.minimax";
    }

    @Override
    protected String defaultReasoningMappingTemplate(AdapterType adapterType) {
        return switch (adapterType) {
            case MINIMAX_MESSAGES, MINIMAX_CHAT -> "reasoning.minimax";
            case MINIMAX_RESPONSES -> "reasoning.responses-effort";
            default -> null;
        };
    }

    @Override
    public int maxEmbeddingsPerCall() {
        return 0;
    }

    @Override
    public boolean supportsParallelCalls() {
        return false;
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey, String modelId) {
        return new MiniMaxMessagesModel(chatCompletionsOptions(provider, apiKey, modelId, Map.of()),
            webClientBuilder(provider));
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey, ProviderModelRef model) {
        var resolved = resolveModel(model);
        var options = chatCompletionsOptions(provider, apiKey, resolved.modelId(), Map.of());
        return switch (resolved.adapterType()) {
            case MINIMAX_MESSAGES -> new MiniMaxMessagesModel(options,
                webClientBuilder(provider));
            case MINIMAX_CHAT -> new MiniMaxChatModel(options, webClientBuilder(provider));
            case MINIMAX_RESPONSES -> new MiniMaxResponsesModel(options,
                webClientBuilder(provider));
            default -> throw new IllegalArgumentException(
                "Unsupported MiniMax language adapter: " + resolved.adapterType());
        };
    }

    @Override
    public ProviderImageGenerationClient buildImageGenerationClient(AiProvider provider,
        String apiKey, String modelId) {
        return new MiniMaxImageGenerationClient(new ImageGenerationClientOptions(
            getProviderType(), resolveBaseUrl(provider), apiKey, modelId, null),
            webClientBuilder(provider));
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions() {
        var reasoning = ReasoningControlOptions.unsupported();
        var optionsFactory = chatCompletionsOptionsFactory(
            reasoning, false, StructuredOutputSupport.PROMPT_ONLY);
        return LanguageModelProviderOptions.builder()
            .reasoningHistorySupported(true)
            .streamToolCallsForReasoning(true)
            .requestHeadersSupported(true)
            .seedSupported(false)
            .nativeStrictToolSchemas(false)
            .structuredOutputSupport(StructuredOutputSupport.PROMPT_ONLY)
            .chatOptionsFactory(optionsFactory::basic)
            .toolCallingChatOptionsFactory(optionsFactory::toolCalling)
            .structuredOutputChatOptionsFactory(optionsFactory::structured)
            .reasoningControlOptions(reasoning)
            .reasoningContentExtractor(this::reasoningContent)
            .build();
    }

    @Override
    public Mono<List<DiscoveredModel>> discoverModels(AiProvider provider, String apiKey) {
        return discoverIdentifierOnlyModels(provider, apiKey, "/v1/models");
    }

    @Override
    public Map<ModelParameter, DefaultParameterMapping> getDefaultParameterMappings() {
        var defaults = new EnumMap<ModelParameter, DefaultParameterMapping>(ModelParameter.class);
        defaults.putAll(super.getDefaultParameterMappings());
        defaults.put(ModelParameter.MAX_OUTPUT_TOKENS,
            DefaultParameterMapping.template("openai.max-tokens"));
        for (var parameter : List.of(ModelParameter.TOP_K, ModelParameter.MIN_P,
            ModelParameter.PRESENCE_PENALTY, ModelParameter.FREQUENCY_PENALTY,
            ModelParameter.REPETITION_PENALTY, ModelParameter.STOP_SEQUENCES,
            ModelParameter.SEED, ModelParameter.LOGPROBS, ModelParameter.TOP_LOGPROBS,
            ModelParameter.PARALLEL_TOOL_CALLS)) {
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
