package run.halo.aifoundation.provider;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.openai.OpenAiEmbeddingOptionsFactory;
import run.halo.aifoundation.provider.support.openai.OpenAiReasoningOptions;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;

@Component
public class AiHubMixProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "https://aihubmix.com/v1";
    private static final String COMPLETIONS_PATH = "/chat/completions";
    private static final String EMBEDDINGS_PATH = "/embeddings";
    private static final String MODEL_CATALOG_PATH = "/api/v1/models";
    private static final String APP_CODE = "NEUE3459";

    @Override
    public String getProviderType() {
        return "aihubmix";
    }

    @Override
    public String getDisplayName() {
        return "AIHubMix";
    }

    @Override
    public String getDescription() {
        return "AIHubMix 提供的多模型 API 聚合平台，支持 Claude、GPT、Gemini 等主流模型。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/aihubmix.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://aihubmix.com";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://docs.aihubmix.com";
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
        return List.of(AdapterType.OPENAI_CHAT, AdapterType.OPENAI_EMBEDDING);
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey, String modelId) {
        return buildOpenAiCompatibleChatModel(provider, apiKey, modelId,
            Map.of("APP-Code", APP_CODE));
    }

    @Override
    public EmbeddingModel buildEmbeddingModel(AiProvider provider, String apiKey, String modelId) {
        return buildOpenAiCompatibleEmbeddingModel(provider, apiKey, modelId,
            Map.of("APP-Code", APP_CODE));
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions() {
        var reasoningControlOptions =
            ReasoningControlOptions.openAiCompatibleEffort(OpenAiReasoningOptions::applyEffort);
        return openAiCompatibleLanguageModelProviderOptions(reasoningControlOptions, null);
    }

    @Override
    public Mono<List<DiscoveredModel>> discoverModels(AiProvider provider, String apiKey) {
        var wc = discoveryWebClientBuilder(provider)
            .baseUrl(modelCatalogBaseUrl(provider))
            .build();
        var requestSpec = wc.get().uri(MODEL_CATALOG_PATH);
        if (apiKey != null && !apiKey.isBlank()) {
            requestSpec = requestSpec.header("Authorization", "Bearer " + apiKey);
        }
        customizeDiscoveryRequest(requestSpec);
        return requestSpec.retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
            })
            .map(json -> {
                var data = listValue(json, "data");
                if (data == null) {
                    return List.<DiscoveredModel>of();
                }
                return discoveredModelsFromNodes(data, "model_id", this::toDiscoveredModel);
            });
    }

    @Override
    protected void customizeDiscoveryRequest(WebClient.RequestHeadersSpec<?> requestSpec) {
        requestSpec.header("APP-Code", APP_CODE);
    }

    @Override
    public EmbeddingModelProviderOptions embeddingModelProviderOptions() {
        return new EmbeddingModelProviderOptions("openai", OpenAiEmbeddingOptionsFactory::build);
    }

    private String modelCatalogBaseUrl(AiProvider provider) {
        var baseUrl = trimTrailingSlash(resolveBaseUrl(provider));
        if (baseUrl.endsWith("/v1")) {
            return baseUrl.substring(0, baseUrl.length() - "/v1".length());
        }
        return baseUrl;
    }

    private String trimTrailingSlash(String baseUrl) {
        var result = baseUrl;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private DiscoveredModel toDiscoveredModel(Map<?, ?> node) {
        var modelId = stringValue(node, "model_id");
        if (modelId.isBlank()) {
            modelId = stringValue(node, "id");
        }
        if (modelId.isBlank()) {
            return null;
        }

        if (containsToken(node.get("types"), "embedding")
            || containsToken(node.get("type"), "embedding")) {
            return remoteDiscoveredModel(modelId, ModelType.EMBEDDING, Set.of(),
                AdapterType.OPENAI_EMBEDDING);
        }

        if (containsLanguageType(node)) {
            return remoteDiscoveredModel(modelId, ModelType.LANGUAGE, languageFeatures(node),
                AdapterType.OPENAI_CHAT);
        }

        return null;
    }

    private boolean containsLanguageType(Map<?, ?> node) {
        var types = node.get("types");
        var type = node.get("type");
        return containsToken(types, "chat")
            || containsToken(types, "llm")
            || containsToken(types, "language")
            || containsToken(type, "chat")
            || containsToken(type, "llm")
            || containsToken(type, "language");
    }

    private Set<ModelFeature> languageFeatures(Map<?, ?> node) {
        var features = new LinkedHashSet<ModelFeature>();
        features.add(ModelFeature.STREAMING);
        if (containsToken(node.get("input_modalities"), "image")
            || containsToken(node.get("features"), "vision")
            || containsToken(node.get("features"), "image")) {
            features.add(ModelFeature.VISION);
        }
        if (containsToken(node.get("features"), "tool")
            || containsToken(node.get("features"), "function")) {
            features.add(ModelFeature.TOOL_CALL);
        }
        if (containsToken(node.get("features"), "json")
            || containsToken(node.get("features"), "structured")) {
            features.add(ModelFeature.STRUCTURED_OUTPUT);
        }
        if (containsToken(node.get("features"), "reasoning")
            || containsToken(node.get("features"), "thinking")) {
            features.add(ModelFeature.REASONING);
        }
        return Set.copyOf(features);
    }
}
