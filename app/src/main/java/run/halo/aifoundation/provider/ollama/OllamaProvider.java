package run.halo.aifoundation.provider.ollama;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.AbstractAiProviderType;
import run.halo.aifoundation.provider.mapping.DefaultParameterMapping;
import run.halo.aifoundation.provider.mapping.ModelParameter;
import run.halo.aifoundation.provider.mapping.ProviderParameterMappingDefaults;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderFeatureSets;
import run.halo.aifoundation.provider.support.ProviderImageGenerationClient;
import run.halo.aifoundation.provider.support.ProviderModelRef;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;
import run.halo.aifoundation.provider.support.StructuredOutputSupport;
import run.halo.aifoundation.provider.support.image.ImageGenerationClientOptions;
import run.halo.aifoundation.provider.transport.ProviderHttpResponseSupport;

/** Ollama-native provider integration for local and Ollama Cloud models. */
@Slf4j
@Component
public class OllamaProvider extends AbstractAiProviderType {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_BASE_URL = "http://localhost:11434";

    @Override
    public String getProviderType() {
        return "ollama";
    }

    @Override
    public String getDisplayName() {
        return "Ollama";
    }

    @Override
    public String getDescription() {
        return "Ollama 原生本地与云端模型集成，支持思考、视觉、工具、结构化输出和嵌入。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/ollama.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://ollama.com";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://docs.ollama.com/api/introduction";
    }

    @Override
    public boolean isBuiltIn() {
        return false;
    }

    @Override
    public boolean requiresBaseUrl() {
        return true;
    }

    @Override
    public String getDefaultBaseUrl() {
        return DEFAULT_BASE_URL;
    }

    @Override
    public String getCompletionsPath() {
        return "/api/chat";
    }

    @Override
    public List<AdapterType> getSupportedAdapterTypes() {
        return List.of(AdapterType.OLLAMA_CHAT, AdapterType.OLLAMA_OPENAI_CHAT,
            AdapterType.OLLAMA_RESPONSES, AdapterType.OLLAMA_MESSAGES,
            AdapterType.OLLAMA_EMBEDDING,
            AdapterType.OLLAMA_IMAGE);
    }

    @Override
    public List<ModelFeature> getSupportedFeatures() {
        return ProviderFeatureSets.VISION_REASONING;
    }

    @Override
    protected String defaultReasoningMappingTemplate() {
        return "reasoning.ollama-think";
    }

    @Override
    protected String defaultReasoningMappingTemplate(AdapterType adapterType) {
        return switch (adapterType) {
            case OLLAMA_CHAT -> "reasoning.ollama-think";
            case OLLAMA_MESSAGES -> "reasoning.messages-thinking";
            default -> null;
        };
    }

    @Override
    public Map<ModelParameter, DefaultParameterMapping> getDefaultParameterMappings(
        AdapterType adapterType) {
        if (adapterType == null) {
            return super.getDefaultParameterMappings();
        }
        var defaults = new EnumMap<ModelParameter, DefaultParameterMapping>(ModelParameter.class);
        defaults.putAll(ProviderParameterMappingDefaults.forAdapters(List.of(adapterType)));
        var reasoningTemplate = defaultReasoningMappingTemplate(adapterType);
        if (reasoningTemplate == null) {
            return Map.copyOf(defaults);
        }
        defaults.put(ModelParameter.REASONING,
            DefaultParameterMapping.template(reasoningTemplate));
        return Map.copyOf(defaults);
    }

    @Override
    public List<ModelFeature> getSupportedFeatures(AdapterType adapterType) {
        return switch (adapterType) {
            case OLLAMA_RESPONSES -> ProviderFeatureSets.REASONING_TEXT;
            case OLLAMA_CHAT, OLLAMA_OPENAI_CHAT, OLLAMA_MESSAGES ->
                ProviderFeatureSets.VISION_REASONING;
            default -> List.of();
        };
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey, String modelId) {
        return new OllamaChatModel(resolveBaseUrl(provider), apiKey,
            OllamaChatOptions.builder().model(modelId).build(), webClientBuilder(provider));
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey, ProviderModelRef model) {
        var resolved = resolveModel(model);
        if (resolved.adapterType() == AdapterType.OLLAMA_CHAT) {
            return buildChatModel(provider, apiKey, resolved.modelId());
        }
        var options = chatCompletionsOptions(provider, apiKey, resolved.modelId(), Map.of())
            .mutate()
            .baseUrl(OllamaEndpoints.openAiBaseUrl(resolveBaseUrl(provider)))
            .build();
        return switch (resolved.adapterType()) {
            case OLLAMA_OPENAI_CHAT -> new OllamaOpenAiChatModel(options.mutate()
                .endpointPath("/chat/completions").build(), webClientBuilder(provider));
            case OLLAMA_RESPONSES -> new OllamaResponsesModel(options.mutate()
                .endpointPath("/responses").build(), webClientBuilder(provider));
            case OLLAMA_MESSAGES -> new OllamaMessagesModel(options.mutate()
                .baseUrl(OllamaEndpoints.nativeBaseUrl(resolveBaseUrl(provider))).build(),
                webClientBuilder(provider));
            default -> throw new IllegalArgumentException(
                "Unsupported Ollama language adapter: " + resolved.adapterType());
        };
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions() {
        return LanguageModelProviderOptions.builder()
            .reasoningHistorySupported(true)
            .streamToolCallsForReasoning(true)
            .requestHeadersSupported(true)
            .seedSupported(true)
            .nativeStrictToolSchemas(false)
            .structuredOutputSupport(StructuredOutputSupport.JSON_SCHEMA)
            .chatOptionsFactory(OllamaChatOptionsSupport::basic)
            .toolCallingChatOptionsFactory(OllamaChatOptionsSupport::tools)
            .structuredOutputChatOptionsFactory(OllamaChatOptionsSupport::structured)
            .nativeOptionsApplicator(OllamaChatOptionsSupport::applyNativeOptions)
            .reasoningControlOptions(ReasoningControlOptions.unsupported())
            .reasoningContentExtractor(this::reasoningContent)
            .build();
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions(AdapterType adapterType) {
        if (adapterType == AdapterType.OLLAMA_RESPONSES) {
            return promptOnlyLanguageModelProviderOptions();
        }
        if (adapterType == AdapterType.OLLAMA_MESSAGES) {
            return promptOnlyLanguageModelProviderOptions();
        }
        return languageModelProviderOptions();
    }

    private LanguageModelProviderOptions promptOnlyLanguageModelProviderOptions() {
        var reasoning = ReasoningControlOptions.unsupported();
        var optionsFactory = chatCompletionsOptionsFactory(
            reasoning, false, StructuredOutputSupport.PROMPT_ONLY);
        return LanguageModelProviderOptions.builder()
            .reasoningHistorySupported(true)
            .streamToolCallsForReasoning(true)
            .requestHeadersSupported(true)
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
    public EmbeddingModel buildEmbeddingModel(AiProvider provider, String apiKey, String modelId) {
        var defaults = new OllamaEmbeddingOptions(modelId, null, null, Map.of());
        return new OllamaEmbeddingModel(resolveBaseUrl(provider), apiKey, defaults,
            webClientBuilder(provider));
    }

    @Override
    public EmbeddingModelProviderOptions embeddingModelProviderOptions() {
        return new EmbeddingModelProviderOptions(OllamaEmbeddingOptionsFactory::build);
    }

    @Override
    public boolean supportsParallelCalls() {
        return false;
    }

    @Override
    public ProviderImageGenerationClient buildImageGenerationClient(AiProvider provider,
        String apiKey, String modelId) {
        return new OllamaImageGenerationClient(new ImageGenerationClientOptions(
            getProviderType(), OllamaEndpoints.openAiBaseUrl(resolveBaseUrl(provider)), apiKey,
            modelId, null), webClientBuilder(provider));
    }

    @Override
    public Mono<List<DiscoveredModel>> discoverModels(AiProvider provider, String apiKey) {
        var webClient = webClientBuilder(provider).build();
        var tagsUrl = OllamaEndpoints.nativeUrl(resolveBaseUrl(provider), "/tags");
        return webClient.get().uri(URI.create(tagsUrl))
            .headers(headers -> applyAuth(headers, apiKey))
            .exchangeToMono(response -> {
                if (!response.statusCode().is2xxSuccessful()) {
                    return response.createException().flatMap(Mono::error);
                }
                return response.bodyToMono(Map.class);
            })
            .flatMapMany(root -> Flux.fromIterable(modelNames(root)))
            .flatMap(modelId -> showModel(provider, apiKey, modelId)
                .map(details -> discoveredModel(modelId, details))
                .onErrorResume(error -> {
                    log.warn("Skipping Ollama model {} because /api/show failed: {}", modelId,
                        error.getMessage());
                    return Mono.empty();
                }), 4)
            .filter(java.util.Objects::nonNull)
            .collectList();
    }

    private Mono<Map<?, ?>> showModel(AiProvider provider, String apiKey, String modelId) {
        var diagnostics = run.halo.aifoundation.provider.transport.ProviderDiagnostics.create(
            getProviderType(), "model-discovery");
        var url = OllamaEndpoints.nativeUrl(resolveBaseUrl(provider), "/show");
        var body = Map.of("model", modelId, "verbose", false);
        diagnostics.request(url, body, false);
        return webClientBuilder(provider).build().post().uri(URI.create(url))
            .headers(headers -> {
                headers.setContentType(MediaType.APPLICATION_JSON);
                applyAuth(headers, apiKey);
            })
            .bodyValue(body)
            .exchangeToMono(response -> {
                if (!response.statusCode().is2xxSuccessful()) {
                    return ProviderHttpResponseSupport.errorMono(response, getProviderType(),
                        "model-show", diagnostics);
                }
                return ProviderHttpResponseSupport.body(response, diagnostics)
                    .map(this::modelDetails);
            });
    }

    private Map<?, ?> modelDetails(String data) {
        try {
            return OBJECT_MAPPER.readValue(data, Map.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Failed to parse Ollama model details", error);
        }
    }

    private List<String> modelNames(Map<?, ?> root) {
        var values = root.get("models") instanceof List<?> list ? list : List.of();
        var names = new ArrayList<String>();
        for (var value : values) {
            if (value instanceof Map<?, ?> model) {
                var name = string(model.get("name"));
                if (!name.isBlank()) {
                    names.add(name);
                }
            }
        }
        return List.copyOf(names);
    }

    private DiscoveredModel discoveredModel(String modelId, Map<?, ?> details) {
        var capabilities = normalizedValues(details.get("capabilities"));
        var modelType = modelType(capabilities);
        if (modelType == null) {
            log.debug("Ignoring Ollama model {} with unrecognized capabilities {}", modelId,
                capabilities);
            return null;
        }
        var adapter = switch (modelType) {
            case LANGUAGE -> AdapterType.OLLAMA_CHAT;
            case EMBEDDING -> AdapterType.OLLAMA_EMBEDDING;
            case IMAGE_GENERATION -> AdapterType.OLLAMA_IMAGE;
            default -> null;
        };
        var features = new LinkedHashSet<ModelFeature>();
        if (modelType == ModelType.LANGUAGE) {
            features.add(ModelFeature.STREAMING);
            features.add(ModelFeature.STRUCTURED_OUTPUT);
            if (capabilities.contains("vision")) {
                features.add(ModelFeature.VISION);
            }
            if (capabilities.contains("tools") || capabilities.contains("tool")) {
                features.add(ModelFeature.TOOL_CALL);
            }
            if (capabilities.contains("thinking")) {
                features.add(ModelFeature.REASONING);
            }
        }
        return DiscoveredModel.builder()
            .modelId(modelId)
            .displayName(modelId)
            .modelType(modelType)
            .features(Set.copyOf(features))
            .adapterType(adapter)
            .source(DiscoverySource.REMOTE)
            .confidence(DiscoveryConfidence.HIGH)
            .build();
    }

    private ModelType modelType(Set<String> capabilities) {
        if (capabilities.contains("embedding")) {
            return ModelType.EMBEDDING;
        }
        if (capabilities.contains("image") || capabilities.contains("image_generation")
            || capabilities.contains("image-generation")) {
            return ModelType.IMAGE_GENERATION;
        }
        if (capabilities.contains("completion")) {
            return ModelType.LANGUAGE;
        }
        return null;
    }

    private Set<String> normalizedValues(Object value) {
        if (!(value instanceof List<?> list)) {
            return Set.of();
        }
        var values = new LinkedHashSet<String>();
        list.forEach(item -> {
            if (item != null) {
                values.add(item.toString().trim().toLowerCase(Locale.ROOT));
            }
        });
        return Set.copyOf(values);
    }

    private void applyAuth(org.springframework.http.HttpHeaders headers, String apiKey) {
        if (apiKey != null && !apiKey.isBlank()) {
            headers.setBearerAuth(apiKey);
        }
    }

    private String reasoningContent(AssistantMessage message) {
        if (message == null || message.getMetadata() == null) {
            return null;
        }
        for (var key : List.of("reasoningContent", "thinking")) {
            var value = message.getMetadata().get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }

    private String string(Object value) {
        return value != null ? value.toString() : "";
    }
}
