package run.halo.aifoundation.provider.kimi;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.capability.CapabilitySource;
import run.halo.aifoundation.capability.InputSource;
import run.halo.aifoundation.capability.LanguageCapability;
import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.capability.ModelCapabilitySources;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.AbstractAiProviderType;
import run.halo.aifoundation.provider.mapping.DefaultParameterMapping;
import run.halo.aifoundation.provider.mapping.ModelParameter;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderFeatureSets;
import run.halo.aifoundation.provider.support.ProviderModelRef;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;
import run.halo.aifoundation.provider.support.StructuredOutputSupport;

/** Moonshot Kimi integration backed by its documented Chat Completions contract. */
@Component
public class KimiProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "https://api.moonshot.cn/v1";

    @Override
    public String getProviderType() {
        return "kimi";
    }

    @Override
    public String getDisplayName() {
        return "月之暗面 Kimi";
    }

    @Override
    public String getDescription() {
        return "Kimi 原生对话服务，支持显式推理映射、结构化输出、Partial Mode 和图像/视频理解。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/kimi.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://www.moonshot.cn";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://platform.kimi.com/docs/api/overview";
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
        return List.of(AdapterType.KIMI_CHAT);
    }

    @Override
    public List<ModelFeature> getSupportedFeatures() {
        return ProviderFeatureSets.VISION_REASONING;
    }

    @Override
    protected String defaultReasoningMappingTemplate() {
        return "reasoning.kimi";
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
        return new KimiChatModel(chatCompletionsOptions(provider, apiKey, modelId, Map.of()),
            webClientBuilder(provider));
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions() {
        return languageModelProviderOptions(ReasoningControlOptions.unsupported());
    }

    private LanguageModelProviderOptions languageModelProviderOptions(
        ReasoningControlOptions reasoning) {
        var optionsFactory = chatCompletionsOptionsFactory(
            reasoning, true, StructuredOutputSupport.JSON_SCHEMA);
        return chatCompletionsProviderOptionsBuilder()
            .reasoningHistorySupported(true)
            .streamToolCallsForReasoning(true)
            .requestHeadersSupported(true)
            .nativeStrictToolSchemas(true)
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
        return getDiscoveryJson(provider, apiKey,
            uriBuilder -> uriBuilder.path("/models").build(), this::customizeDiscoveryRequest)
            .map(root -> {
                var data = listValue(root, "data");
                return data == null ? List.of()
                    : discoveredModelsFromNodes(data, "id", this::remoteLanguageModel);
            });
    }

    @Override
    public Map<ModelParameter, DefaultParameterMapping> getDefaultParameterMappings() {
        var defaults = new EnumMap<ModelParameter, DefaultParameterMapping>(ModelParameter.class);
        defaults.putAll(super.getDefaultParameterMappings());
        defaults.put(ModelParameter.MAX_OUTPUT_TOKENS,
            DefaultParameterMapping.template("openai.max-completion-tokens"));
        for (var parameter : List.of(ModelParameter.TOP_K, ModelParameter.MIN_P,
            ModelParameter.REPETITION_PENALTY, ModelParameter.SEED,
            ModelParameter.PARALLEL_TOOL_CALLS)) {
            defaults.put(parameter, DefaultParameterMapping.unsupported());
        }
        return Map.copyOf(defaults);
    }

    private DiscoveredModel remoteLanguageModel(Map<?, ?> node) {
        var modelId = stringValue(node, "id");
        if (modelId.isBlank()) {
            return null;
        }
        var supportsImage = booleanValue(node, "supports_image_in");
        var supportsVideo = booleanValue(node, "supports_video_in");
        var supportsReasoning = booleanValue(node, "supports_reasoning");
        var features = new LinkedHashSet<ModelFeature>();
        features.add(ModelFeature.STREAMING);
        if (supportsImage) {
            features.add(ModelFeature.VISION);
        }
        if (supportsReasoning) {
            features.add(ModelFeature.REASONING);
        }
        var capabilities = capabilities(supportsImage, supportsVideo, supportsReasoning);
        return DiscoveredModel.builder()
            .modelId(modelId)
            .displayName(modelId)
            .modelType(ModelType.LANGUAGE)
            .features(Set.copyOf(features))
            .adapterType(AdapterType.KIMI_CHAT)
            .source(DiscoverySource.REMOTE)
            .confidence(DiscoveryConfidence.HIGH)
            .capabilities(capabilities)
            .capabilitySources(capabilitySources(capabilities))
            .build();
    }

    private ModelCapabilitySources capabilitySources(ModelCapabilities capabilities) {
        if (capabilities == null) {
            return ModelCapabilitySources.unknown();
        }
        return ModelCapabilitySources.builder()
            .language(CapabilitySource.REMOTE)
            .build();
    }

    private ModelCapabilities capabilities(boolean image, boolean video, boolean reasoning) {
        if (!hasCapabilities(image, video, reasoning)) {
            return null;
        }
        var mediaTypes = new ArrayList<String>();
        if (image) {
            mediaTypes.add("image/*");
        }
        if (video) {
            mediaTypes.add("video/*");
        }
        return ModelCapabilities.builder()
            .language(LanguageCapability.builder()
                .imageInput(image)
                .fileInput(video)
                .reasoningHistory(reasoning)
                .inputMediaTypes(List.copyOf(mediaTypes))
                .inputSources(!mediaTypes.isEmpty()
                    ? List.of(InputSource.DATA) : List.of())
                .build())
            .build();
    }

    private boolean hasCapabilities(boolean image, boolean video, boolean reasoning) {
        if (image) {
            return true;
        }
        if (video) {
            return true;
        }
        return reasoning;
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
