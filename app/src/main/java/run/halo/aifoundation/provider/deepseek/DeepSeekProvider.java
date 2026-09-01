package run.halo.aifoundation.provider.deepseek;

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
import run.halo.aifoundation.provider.support.ProviderFeatureSets;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;
import run.halo.aifoundation.provider.support.StructuredOutputSupport;

@Component
public class DeepSeekProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";

    @Override
    public String getProviderType() {
        return "deepseek";
    }

    @Override
    public String getDisplayName() {
        return "深度求索 DeepSeek";
    }

    @Override
    public String getDescription() {
        return "DeepSeek 原生模型服务，提供 Chat Completions、Responses 与 Messages；视觉和推理能力按所选模型映射。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/deepseek.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://deepseek.com";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://api-docs.deepseek.com";
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
        return List.of(AdapterType.DEEPSEEK_CHAT, AdapterType.DEEPSEEK_RESPONSES,
            AdapterType.DEEPSEEK_MESSAGES);
    }

    @Override
    public List<ModelFeature> getSupportedFeatures() {
        return ProviderFeatureSets.VISION_REASONING;
    }

    @Override
    protected String defaultReasoningMappingTemplate() {
        return "reasoning.deepseek";
    }

    @Override
    protected String defaultReasoningMappingTemplate(AdapterType adapterType) {
        return switch (adapterType) {
            case DEEPSEEK_RESPONSES -> "reasoning.responses-effort";
            case DEEPSEEK_CHAT -> "reasoning.deepseek";
            case DEEPSEEK_MESSAGES -> "reasoning.deepseek-messages";
            default -> null;
        };
    }

    @Override
    public List<ModelFeature> getSupportedFeatures(AdapterType adapterType) {
        return switch (adapterType) {
            case DEEPSEEK_CHAT, DEEPSEEK_RESPONSES, DEEPSEEK_MESSAGES ->
                ProviderFeatureSets.VISION_REASONING;
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
        return new DeepSeekChatModel(chatCompletionsOptions(provider, apiKey, modelId, Map.of()),
            webClientBuilder(provider));
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey,
        run.halo.aifoundation.provider.support.ProviderModelRef model) {
        var resolved = resolveModel(model);
        var options = chatCompletionsOptions(provider, apiKey, resolved.modelId(), Map.of());
        return switch (resolved.adapterType()) {
            case DEEPSEEK_CHAT -> new DeepSeekChatModel(options, webClientBuilder(provider));
            case DEEPSEEK_RESPONSES -> new DeepSeekResponsesModel(options,
                webClientBuilder(provider));
            case DEEPSEEK_MESSAGES -> new DeepSeekMessagesModel(options,
                webClientBuilder(provider));
            default -> throw new IllegalArgumentException(
                "Unsupported DeepSeek language adapter: " + resolved.adapterType());
        };
    }

    @Override
    public Mono<List<DiscoveredModel>> discoverModels(AiProvider provider, String apiKey) {
        return discoverIdentifierOnlyModels(provider, apiKey, "/models");
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions() {
        return languageModelProviderOptions(StructuredOutputSupport.JSON_OBJECT, true);
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions(AdapterType adapterType) {
        return switch (adapterType) {
            case DEEPSEEK_RESPONSES ->
                languageModelProviderOptions(StructuredOutputSupport.JSON_SCHEMA, false);
            case DEEPSEEK_MESSAGES ->
                languageModelProviderOptions(StructuredOutputSupport.PROMPT_ONLY, false);
            default -> languageModelProviderOptions();
        };
    }

    private LanguageModelProviderOptions languageModelProviderOptions(
        StructuredOutputSupport structuredOutput, boolean nativeStrictToolSchemas) {
        var reasoningControlOptions = ReasoningControlOptions.unsupported();
        var optionsFactory = chatCompletionsOptionsFactory(
            reasoningControlOptions, nativeStrictToolSchemas, structuredOutput);
        return chatCompletionsProviderOptionsBuilder()
            .reasoningHistorySupported(true)
            .streamToolCallsForReasoning(true)
            .requestHeadersSupported(true)
            .nativeStrictToolSchemas(nativeStrictToolSchemas)
            .structuredOutputSupport(structuredOutput)
            .chatOptionsFactory(optionsFactory::basic)
            .toolCallingChatOptionsFactory(optionsFactory::toolCalling)
            .structuredOutputChatOptionsFactory(optionsFactory::structured)
            .reasoningControlOptions(reasoningControlOptions)
            .reasoningContentExtractor(this::reasoningContent)
            .build();
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

    @Override
    public Map<ModelParameter, DefaultParameterMapping> getDefaultParameterMappings() {
        var defaults = new EnumMap<ModelParameter, DefaultParameterMapping>(ModelParameter.class);
        defaults.putAll(super.getDefaultParameterMappings());
        for (var parameter : List.of(
            ModelParameter.TOP_K,
            ModelParameter.MIN_P,
            ModelParameter.PRESENCE_PENALTY,
            ModelParameter.FREQUENCY_PENALTY,
            ModelParameter.REPETITION_PENALTY,
            ModelParameter.SEED,
            ModelParameter.PARALLEL_TOOL_CALLS)) {
            defaults.put(parameter, DefaultParameterMapping.unsupported());
        }
        return Map.copyOf(defaults);
    }
}
