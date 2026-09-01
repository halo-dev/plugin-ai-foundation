package run.halo.aifoundation.provider.openrouter;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.capability.CapabilitySource;
import run.halo.aifoundation.capability.ImageGenerationCapability;
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
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderFeatureSets;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;
import run.halo.aifoundation.provider.support.ProviderImageGenerationClient;
import run.halo.aifoundation.provider.support.ProviderModelRef;
import run.halo.aifoundation.provider.support.ProviderRerankingClient;
import run.halo.aifoundation.provider.support.RerankingModelProviderOptions;
import run.halo.aifoundation.provider.support.StructuredOutputSupport;
import run.halo.aifoundation.provider.support.image.ImageGenerationClientOptions;

/** OpenRouter integration using its router-owned Chat, embedding, rerank, and image contracts. */
@Component
@Slf4j
public class OpenRouterProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "https://openrouter.ai/api/v1";

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
        return "OpenRouter 原生路由适配，支持供应商回退、ZDR、成本元数据和独立的嵌入、重排与图像接口。";
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
        return List.of(AdapterType.OPENROUTER_CHAT, AdapterType.OPENROUTER_RESPONSES,
            AdapterType.OPENROUTER_MESSAGES, AdapterType.OPENROUTER_EMBEDDING,
            AdapterType.RERANK, AdapterType.OPENROUTER_IMAGE);
    }

    @Override
    public List<ModelFeature> getSupportedFeatures() {
        return ProviderFeatureSets.ALL;
    }

    @Override
    public List<ModelFeature> getSupportedFeatures(AdapterType adapterType) {
        return switch (adapterType) {
            case OPENROUTER_CHAT, OPENROUTER_RESPONSES -> ProviderFeatureSets.ALL;
            case OPENROUTER_MESSAGES -> ProviderFeatureSets.VISION_REASONING;
            default -> List.of();
        };
    }

    @Override
    protected String defaultReasoningMappingTemplate() {
        return "reasoning.openrouter";
    }

    @Override
    protected String defaultReasoningMappingTemplate(AdapterType adapterType) {
        return switch (adapterType) {
            case OPENROUTER_CHAT -> "reasoning.openrouter";
            case OPENROUTER_RESPONSES -> "reasoning.responses-effort";
            case OPENROUTER_MESSAGES -> "reasoning.openrouter-messages";
            default -> null;
        };
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey, String modelId) {
        return new OpenRouterChatModel(languageOptions(provider, apiKey, modelId),
            webClientBuilder(provider));
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey, ProviderModelRef model) {
        var resolved = resolveModel(model);
        var options = languageOptions(provider, apiKey, resolved.modelId());
        return switch (resolved.adapterType()) {
            case OPENROUTER_CHAT -> new OpenRouterChatModel(options, webClientBuilder(provider));
            case OPENROUTER_RESPONSES -> new OpenRouterResponsesModel(options,
                webClientBuilder(provider));
            case OPENROUTER_MESSAGES -> new OpenRouterMessagesModel(options,
                webClientBuilder(provider));
            default -> throw new IllegalArgumentException(
                "Unsupported OpenRouter language adapter: " + resolved.adapterType());
        };
    }

    @Override
    public EmbeddingModel buildEmbeddingModel(AiProvider provider, String apiKey, String modelId) {
        return new OpenRouterEmbeddingModel(OpenRouterEmbeddingOptions.builder()
            .baseUrl(resolveBaseUrl(provider))
            .apiKey(apiKey)
            .model(modelId)
            .build(), webClientBuilder(provider));
    }

    @Override
    public ProviderRerankingClient buildRerankingClient(AiProvider provider, String apiKey,
        String modelId) {
        return new OpenRouterRerankingClient(resolveBaseUrl(provider), modelId, apiKey,
            webClientBuilder(provider), Map.of());
    }

    @Override
    public ProviderImageGenerationClient buildImageGenerationClient(AiProvider provider,
        String apiKey, String modelId) {
        return new OpenRouterImageGenerationClient(new ImageGenerationClientOptions(
            getProviderType(), resolveBaseUrl(provider), apiKey, modelId,
            Map.of()), webClientBuilder(provider));
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions() {
        return languageModelProviderOptions(true);
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions(AdapterType adapterType) {
        if (adapterType == AdapterType.OPENROUTER_MESSAGES) {
            return languageModelProviderOptions(false);
        }
        return languageModelProviderOptions();
    }

    private LanguageModelProviderOptions languageModelProviderOptions(
        boolean nativeStrictToolSchemas) {
        var reasoning = ReasoningControlOptions.unsupported();
        var optionsFactory = chatCompletionsOptionsFactory(
            reasoning, nativeStrictToolSchemas, StructuredOutputSupport.JSON_SCHEMA);
        return LanguageModelProviderOptions.builder()
            .reasoningHistorySupported(true)
            .streamToolCallsForReasoning(true)
            .requestHeadersSupported(true)
            .seedSupported(true)
            .nativeStrictToolSchemas(nativeStrictToolSchemas)
            .structuredOutputSupport(StructuredOutputSupport.JSON_SCHEMA)
            .chatOptionsFactory(optionsFactory::basic)
            .toolCallingChatOptionsFactory(optionsFactory::toolCalling)
            .structuredOutputChatOptionsFactory(optionsFactory::structured)
            .reasoningControlOptions(reasoning)
            .reasoningContentExtractor(this::reasoningContent)
            .build();
    }

    @Override
    public EmbeddingModelProviderOptions embeddingModelProviderOptions() {
        return new EmbeddingModelProviderOptions(OpenRouterEmbeddingOptionsFactory::build);
    }

    @Override
    public Mono<List<DiscoveredModel>> discoverModels(AiProvider provider, String apiKey) {
        return Mono.zip(discoverLanguageModels(provider, apiKey),
                discoverTypedModels(provider, apiKey, "/embeddings/models", ModelType.EMBEDDING,
                    AdapterType.OPENROUTER_EMBEDDING),
                discoverRerankModels(provider, apiKey),
                discoverImageModels(provider, apiKey))
            .map(tuple -> {
                var models = new LinkedHashMap<String, DiscoveredModel>();
                tuple.getT1().forEach(model -> models.put(key(model), model));
                tuple.getT2().forEach(model -> models.put(key(model), model));
                tuple.getT3().forEach(model -> models.put(key(model), model));
                tuple.getT4().forEach(model -> models.put(key(model), model));
                return List.copyOf(models.values());
            });
    }

    @Override
    public Map<ModelParameter, DefaultParameterMapping> getDefaultParameterMappings() {
        var defaults = new EnumMap<ModelParameter, DefaultParameterMapping>(ModelParameter.class);
        defaults.putAll(super.getDefaultParameterMappings());
        defaults.put(ModelParameter.MAX_OUTPUT_TOKENS,
            DefaultParameterMapping.template("openai.max-completion-tokens"));
        return Map.copyOf(defaults);
    }

    private Mono<List<DiscoveredModel>> discoverLanguageModels(AiProvider provider, String apiKey) {
        return discovery(provider, apiKey, "/models").map(nodes ->
            discoveredModelsFromNodes(nodes, "id", this::languageModel));
    }

    private run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions
        languageOptions(AiProvider provider, String apiKey, String modelId) {
        return chatCompletionsOptions(provider, apiKey, modelId,
            Map.of("X-OpenRouter-Metadata", "enabled"));
    }

    private Mono<List<DiscoveredModel>> discoverTypedModels(AiProvider provider, String apiKey,
        String path, ModelType type, AdapterType adapter) {
        return discovery(provider, apiKey, path).map(nodes -> discoveredModelsFromNodes(nodes,
            "id", node -> typedModel(node, type, adapter)));
    }

    private Mono<List<DiscoveredModel>> discoverImageModels(AiProvider provider, String apiKey) {
        return discovery(provider, apiKey, "/images/models").map(nodes ->
            discoveredModelsFromNodes(nodes, "id", this::imageModel));
    }

    private Mono<List<DiscoveredModel>> discoverRerankModels(AiProvider provider, String apiKey) {
        return discovery(provider, apiKey, "/models?output_modalities=rerank")
            .map(nodes -> discoveredModelsFromNodes(nodes, "id", node -> {
                var outputs = architectureModalities(node, "output_modalities");
                return outputs.contains("rerank")
                    ? typedModel(node, ModelType.RERANK, AdapterType.RERANK) : null;
            }));
    }

    private Mono<List<?>> discovery(AiProvider provider, String apiKey, String path) {
        var queryIndex = path.indexOf('?');
        var pathname = queryIndex >= 0 ? path.substring(0, queryIndex) : path;
        var query = queryIndex >= 0 ? path.substring(queryIndex + 1) : null;
        return getDiscoveryJson(provider, apiKey, uriBuilder -> {
            var builder = uriBuilder.path(pathname);
            if (query != null) {
                for (var pair : query.split("&")) {
                    var parts = pair.split("=", 2);
                    builder.queryParam(parts[0], parts.length > 1 ? parts[1] : "");
                }
            }
            return builder.build();
        }, this::customizeDiscoveryRequest).map(root -> {
            var data = listValue(root, "data");
            return data != null ? data : List.of();
        }).onErrorResume(error -> {
            log.warn("OpenRouter model discovery endpoint {} failed: {}", path,
                error.getMessage());
            return Mono.just(List.of());
        });
    }

    private DiscoveredModel languageModel(Map<?, ?> node) {
        var modelId = stringValue(node, "id");
        if (modelId.isBlank()) {
            return null;
        }
        var parameters = stringSet(node.get("supported_parameters"));
        var inputs = architectureModalities(node, "input_modalities");
        var features = new LinkedHashSet<ModelFeature>();
        features.add(ModelFeature.STREAMING);
        if (inputs.contains("image")) {
            features.add(ModelFeature.VISION);
        }
        if (inputs.contains("audio")) {
            features.add(ModelFeature.AUDIO_INPUT);
        }
        if (containsAny(parameters, "tools", "tool_choice")) {
            features.add(ModelFeature.TOOL_CALL);
        }
        if (containsAny(parameters, "response_format", "structured_outputs")) {
            features.add(ModelFeature.STRUCTURED_OUTPUT);
        }
        var reasoning = containsAny(parameters, "reasoning", "include_reasoning",
            "reasoning_effort");
        if (reasoning) {
            features.add(ModelFeature.REASONING);
        }
        var mediaTypes = new ArrayList<String>();
        inputs.forEach(input -> {
            switch (input) {
                case "image" -> mediaTypes.add("image/*");
                case "audio" -> mediaTypes.add("audio/*");
                case "video" -> mediaTypes.add("video/*");
                case "file" -> mediaTypes.add("application/*");
                default -> {
                }
            }
        });
        var capability = LanguageCapability.builder()
            .imageInput(inputs.contains("image"))
            .fileInput(containsAny(inputs, "file", "audio", "video"))
            .reasoningHistory(reasoning)
            .inputMediaTypes(List.copyOf(mediaTypes))
            .inputSources(mediaTypes.isEmpty() ? List.of()
                : List.of(InputSource.DATA, InputSource.URL))
            .build();
        return DiscoveredModel.builder()
            .modelId(modelId)
            .displayName(stringValue(node, "name"))
            .modelType(ModelType.LANGUAGE)
            .features(Set.copyOf(features))
            .adapterType(AdapterType.OPENROUTER_CHAT)
            .source(DiscoverySource.REMOTE)
            .confidence(DiscoveryConfidence.HIGH)
            .capabilities(ModelCapabilities.builder().language(capability).build())
            .capabilitySources(ModelCapabilitySources.builder()
                .language(CapabilitySource.REMOTE)
                .build())
            .build();
    }

    private DiscoveredModel typedModel(Map<?, ?> node, ModelType type, AdapterType adapter) {
        var modelId = stringValue(node, "id");
        if (modelId.isBlank()) {
            return null;
        }
        return DiscoveredModel.builder()
            .modelId(modelId)
            .displayName(stringValue(node, "name"))
            .modelType(type)
            .adapterType(adapter)
            .source(DiscoverySource.REMOTE)
            .confidence(DiscoveryConfidence.HIGH)
            .build();
    }

    private DiscoveredModel imageModel(Map<?, ?> node) {
        var modelId = stringValue(node, "id");
        if (modelId.isBlank()) {
            return null;
        }
        var inputs = architectureModalities(node, "input_modalities");
        var supported = node.get("supported_parameters") instanceof Map<?, ?> map ? map : Map.of();
        var capability = ImageGenerationCapability.builder()
            .textToImage(inputs.contains("text"))
            .imageToImage(inputs.contains("image"))
            .maskInput(supported.containsKey("mask"))
            .maxImagesPerCall(rangeMaximum(supported.get("n")))
            .outputMediaTypes(outputMediaTypes(supported.get("output_format")))
            .build();
        return DiscoveredModel.builder()
            .modelId(modelId)
            .displayName(stringValue(node, "name"))
            .modelType(ModelType.IMAGE_GENERATION)
            .adapterType(AdapterType.OPENROUTER_IMAGE)
            .source(DiscoverySource.REMOTE)
            .confidence(DiscoveryConfidence.HIGH)
            .capabilities(ModelCapabilities.builder().imageGeneration(capability).build())
            .capabilitySources(ModelCapabilitySources.builder()
                .imageGeneration(CapabilitySource.REMOTE)
                .build())
            .build();
    }

    private Integer rangeMaximum(Object descriptor) {
        if (!(descriptor instanceof Map<?, ?> values)) {
            return null;
        }
        return values.get("max") instanceof Number maximum ? maximum.intValue() : null;
    }

    private List<String> outputMediaTypes(Object descriptor) {
        if (!(descriptor instanceof Map<?, ?> values)) {
            return List.of();
        }
        if (!(values.get("values") instanceof Iterable<?> formats)) {
            return List.of();
        }
        var mediaTypes = new ArrayList<String>();
        for (var format : formats) {
            var mediaType = imageMediaType(format);
            if (mediaType != null) {
                mediaTypes.add(mediaType);
            }
        }
        return List.copyOf(mediaTypes);
    }

    private String imageMediaType(Object format) {
        if (format == null) {
            return null;
        }
        return switch (format.toString().toLowerCase(Locale.ROOT)) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            default -> null;
        };
    }

    private Set<String> architectureModalities(Map<?, ?> node, String field) {
        return node.get("architecture") instanceof Map<?, ?> architecture
            ? stringSet(architecture.get(field)) : Set.of();
    }

    private Set<String> stringSet(Object value) {
        if (!(value instanceof Iterable<?> values)) {
            return Set.of();
        }
        var result = new LinkedHashSet<String>();
        values.forEach(item -> {
            if (item != null) {
                result.add(item.toString().toLowerCase(Locale.ROOT));
            }
        });
        return Set.copyOf(result);
    }

    private boolean containsAny(Set<String> values, String... candidates) {
        for (var candidate : candidates) {
            if (values.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String key(DiscoveredModel model) {
        return model.modelType() + ":" + model.modelId();
    }

    private String reasoningContent(AssistantMessage message) {
        if (message == null || message.getMetadata() == null) {
            return null;
        }
        for (var key : List.of("reasoningContent", "reasoning", "reasoning_content")) {
            var value = message.getMetadata().get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }
}
