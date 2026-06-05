package run.halo.aifoundation.provider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.OpenAiChatOptionsSupport;
import run.halo.aifoundation.provider.support.OpenAiCompatibleEmbeddingModel;
import run.halo.aifoundation.provider.support.OpenAiEmbeddingOptionsFactory;
import run.halo.aifoundation.provider.support.OpenAiThinkingOptions;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;

@Component
public class SiliconFlowProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "https://api.siliconflow.cn/v1";
    private static final String COMPLETIONS_PATH = "/chat/completions";
    private static final String EMBEDDINGS_PATH = "/embeddings";

    @Override
    public String getProviderType() {
        return "siliconflow";
    }

    @Override
    public String getDisplayName() {
        return "硅基流动";
    }

    @Override
    public String getDescription() {
        return "硅基流动提供的大模型推理平台，聚合多种开源和商业模型。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/siliconcloud.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://www.siliconflow.com";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://docs.siliconflow.com";
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
    public int maxEmbeddingsPerCall() {
        return 32;
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey, String modelId) {
        var openAiApi = buildOpenAiApi(provider, apiKey);
        return OpenAiChatModel.builder()
            .openAiApi(openAiApi)
            .defaultOptions(OpenAiChatOptions.builder().model(modelId).build())
            .build();
    }

    @Override
    public EmbeddingModel buildEmbeddingModel(AiProvider provider, String apiKey, String modelId) {
        var openAiApi = buildOpenAiApi(provider, apiKey);
        return new OpenAiCompatibleEmbeddingModel(openAiApi, modelId);
    }

    @Override
    public Mono<List<DiscoveredModel>> discoverModels(AiProvider provider, String apiKey) {
        return Mono.zip(
            discoverModelsBySubType(provider, apiKey, "chat", ModelType.LANGUAGE,
                AdapterType.OPENAI_CHAT, Set.of(ModelFeature.STREAMING)),
            discoverModelsBySubType(provider, apiKey, "embedding", ModelType.EMBEDDING,
                AdapterType.OPENAI_EMBEDDING, Set.of()),
            (chatModels, embeddingModels) -> {
                var models = new LinkedHashMap<String, DiscoveredModel>();
                chatModels.forEach(model -> models.putIfAbsent(model.modelId(), model));
                embeddingModels.forEach(model -> models.put(model.modelId(), model));
                return List.copyOf(models.values());
            }
        );
    }

    @Override
    public EmbeddingModelProviderOptions embeddingModelProviderOptions() {
        return new EmbeddingModelProviderOptions("openai", OpenAiEmbeddingOptionsFactory::build);
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions() {
        var reasoningControlOptions = ReasoningControlOptions.enableThinking();
        return new LanguageModelProviderOptions(false, false,
            request -> OpenAiChatOptionsSupport.buildBasic(request, getProviderType(),
                reasoningControlOptions, OpenAiThinkingOptions::applyEnableThinking),
            (request, toolCallbacks, toolNames) -> OpenAiChatOptionsSupport.buildToolCalling(
                request, toolCallbacks, toolNames, getProviderType(), reasoningControlOptions,
                OpenAiThinkingOptions::applyEnableThinking),
            request -> OpenAiChatOptionsSupport.buildStructured(request, getProviderType(),
                reasoningControlOptions, OpenAiThinkingOptions::applyEnableThinking),
            reasoningControlOptions);
    }

    private OpenAiApi buildOpenAiApi(AiProvider provider, String apiKey) {
        return OpenAiApi.builder()
            .baseUrl(resolveBaseUrl(provider))
            .apiKey(apiKey)
            .completionsPath(COMPLETIONS_PATH)
            .embeddingsPath(EMBEDDINGS_PATH)
            .webClientBuilder(webClientBuilder(provider))
            .restClientBuilder(restClientBuilder(provider))
            .build();
    }

    private Mono<List<DiscoveredModel>> discoverModelsBySubType(AiProvider provider, String apiKey,
        String subType, ModelType modelType, AdapterType adapterType, Set<ModelFeature> features) {
        return getDiscoveryJson(provider, apiKey,
            uriBuilder -> uriBuilder.path("/models")
                .queryParam("sub_type", subType)
                .build(),
            this::customizeDiscoveryRequest
        ).map(json -> {
            var data = listValue(json, "data");
            if (data == null) {
                return List.<DiscoveredModel>of();
            }
            return discoveredModelsFromNodes(data, "id",
                node -> remoteDiscoveredModel(stringValue(node, "id"), modelType, features,
                    adapterType));
        });
    }
}
