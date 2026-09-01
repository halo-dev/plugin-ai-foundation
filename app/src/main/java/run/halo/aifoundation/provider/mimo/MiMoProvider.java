package run.halo.aifoundation.provider.mimo;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import run.halo.aifoundation.provider.support.ProviderModelRef;
import run.halo.aifoundation.provider.support.ProviderUris;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;
import run.halo.aifoundation.provider.support.StructuredOutputSupport;

/** Xiaomi MiMo integration with distinct Responses and full-modal Chat adapters. */
@Component
public class MiMoProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "https://api.xiaomimimo.com/v1";
    @Override
    public String getProviderType() {
        return "mimo";
    }

    @Override
    public String getDisplayName() {
        return "Xiaomi MiMo";
    }

    @Override
    public String getDescription() {
        return "小米 MiMo 原生 Responses 与全模态 Chat 服务，支持推理、严格工具、联网搜索和图像/音视频理解。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/xiaomimimo.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://mimo.mi.com/";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://mimo.mi.com/docs/en-US/quick-start/summary/welcome";
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
        return List.of(AdapterType.MIMO_RESPONSES, AdapterType.MIMO_CHAT,
            AdapterType.MIMO_MESSAGES);
    }

    @Override
    public List<ModelFeature> getSupportedFeatures() {
        // The Chat adapter is the full-modal provider ceiling. Discovery narrows the default
        // Responses adapter to the capabilities its official wire contract can actually accept.
        return ProviderFeatureSets.ALL;
    }

    @Override
    protected String defaultReasoningMappingTemplate() {
        return "reasoning.responses-effort";
    }

    @Override
    protected String defaultReasoningMappingTemplate(AdapterType adapterType) {
        return switch (adapterType) {
            case MIMO_CHAT -> "reasoning.thinking-type";
            case MIMO_MESSAGES -> "reasoning.messages-thinking";
            case MIMO_RESPONSES -> "reasoning.responses-effort";
            default -> null;
        };
    }

    @Override
    public List<ModelFeature> getSupportedFeatures(AdapterType adapterType) {
        return switch (adapterType) {
            case MIMO_RESPONSES -> ProviderFeatureSets.VISION_REASONING;
            case MIMO_CHAT -> ProviderFeatureSets.ALL;
            case MIMO_MESSAGES -> ProviderFeatureSets.VISION_REASONING;
            default -> List.of();
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
        return responsesModel(provider, apiKey, modelId);
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey, ProviderModelRef model) {
        var resolved = resolveModel(model);
        var options = chatCompletionsOptions(provider, apiKey, resolved.modelId(), Map.of());
        return switch (resolved.adapterType()) {
            case MIMO_RESPONSES -> new MiMoResponsesModel(options, webClientBuilder(provider));
            case MIMO_CHAT -> new MiMoChatModel(options, webClientBuilder(provider));
            case MIMO_MESSAGES -> new MiMoMessagesModel(options.mutate()
                .baseUrl(ProviderUris.withoutTrailingPath(options.getBaseUrl(), "/v1"))
                .build(), webClientBuilder(provider));
            default -> throw new IllegalArgumentException("Unsupported MiMo language adapter: "
                + resolved.adapterType());
        };
    }

    @Override
    public Mono<List<DiscoveredModel>> discoverModels(AiProvider provider, String apiKey) {
        return discoverIdentifierOnlyModels(provider, apiKey, "/models");
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions() {
        return languageModelProviderOptions(
            ReasoningControlOptions.unsupported(), StructuredOutputSupport.JSON_OBJECT, true);
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions(AdapterType adapterType) {
        if (adapterType == AdapterType.MIMO_MESSAGES) {
            return languageModelProviderOptions(
                ReasoningControlOptions.unsupported(), StructuredOutputSupport.PROMPT_ONLY, false);
        }
        return languageModelProviderOptions();
    }

    private LanguageModelProviderOptions languageModelProviderOptions(
        ReasoningControlOptions reasoning, StructuredOutputSupport structuredOutput,
        boolean nativeStrictToolSchemas) {
        var optionsFactory = chatCompletionsOptionsFactory(
            reasoning, nativeStrictToolSchemas, structuredOutput);
        return LanguageModelProviderOptions.builder()
            .reasoningHistorySupported(true)
            .streamToolCallsForReasoning(true)
            .requestHeadersSupported(true)
            .seedSupported(false)
            .nativeStrictToolSchemas(nativeStrictToolSchemas)
            .structuredOutputSupport(structuredOutput)
            .chatOptionsFactory(optionsFactory::basic)
            .toolCallingChatOptionsFactory(optionsFactory::toolCalling)
            .structuredOutputChatOptionsFactory(optionsFactory::structured)
            .reasoningControlOptions(reasoning)
            .reasoningContentExtractor(this::reasoningContent)
            .build();
    }

    @Override
    public Map<ModelParameter, DefaultParameterMapping> getDefaultParameterMappings() {
        var defaults = new EnumMap<ModelParameter, DefaultParameterMapping>(ModelParameter.class);
        defaults.putAll(super.getDefaultParameterMappings());
        defaults.put(ModelParameter.MAX_OUTPUT_TOKENS,
            DefaultParameterMapping.template("openai.max-completion-tokens"));
        for (var parameter : List.of(ModelParameter.TOP_K, ModelParameter.MIN_P,
            ModelParameter.REPETITION_PENALTY, ModelParameter.SEED, ModelParameter.LOGPROBS,
            ModelParameter.TOP_LOGPROBS, ModelParameter.PARALLEL_TOOL_CALLS)) {
            defaults.put(parameter, DefaultParameterMapping.unsupported());
        }
        return Map.copyOf(defaults);
    }

    private ChatModel responsesModel(AiProvider provider, String apiKey, String modelId) {
        return new MiMoResponsesModel(chatCompletionsOptions(provider, apiKey, modelId, Map.of()),
            webClientBuilder(provider));
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
