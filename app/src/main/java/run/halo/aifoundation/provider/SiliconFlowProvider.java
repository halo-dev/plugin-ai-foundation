package run.halo.aifoundation.provider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderRerankingClient;
import run.halo.aifoundation.provider.support.openai.OpenAiEmbeddingOptionsFactory;
import run.halo.aifoundation.provider.support.openai.OpenAiThinkingOptions;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;
import run.halo.aifoundation.provider.support.RerankingModelProviderOptions;
import run.halo.aifoundation.provider.support.rerank.SiliconFlowRerankingClient;

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
        return List.of(AdapterType.OPENAI_CHAT, AdapterType.OPENAI_EMBEDDING, AdapterType.RERANK);
    }

    @Override
    public int maxEmbeddingsPerCall() {
        return 32;
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey, String modelId) {
        return buildOpenAiCompatibleChatModel(provider, apiKey, modelId);
    }

    @Override
    public EmbeddingModel buildEmbeddingModel(AiProvider provider, String apiKey, String modelId) {
        return buildOpenAiCompatibleEmbeddingModel(provider, apiKey, modelId);
    }

    @Override
    public ProviderRerankingClient buildRerankingClient(AiProvider provider, String apiKey,
        String modelId) {
        return new SiliconFlowRerankingClient(trimTrailingSlash(resolveBaseUrl(provider)), modelId,
            apiKey, webClientBuilder(provider));
    }

    @Override
    public Mono<List<DiscoveredModel>> discoverModels(AiProvider provider, String apiKey) {
        return Mono.zip(
            discoverModelsBySubType(provider, apiKey, "chat", ModelType.LANGUAGE,
                AdapterType.OPENAI_CHAT, Set.of(ModelFeature.STREAMING)),
            discoverModelsBySubType(provider, apiKey, "embedding", ModelType.EMBEDDING,
                AdapterType.OPENAI_EMBEDDING, Set.of()),
            discoverModelsBySubType(provider, apiKey, "reranker", ModelType.RERANK,
                AdapterType.RERANK, Set.of())
        ).map(tuple -> {
                var chatModels = tuple.getT1();
                var embeddingModels = tuple.getT2();
                var rerankModels = tuple.getT3();
                var models = new LinkedHashMap<String, DiscoveredModel>();
                chatModels.forEach(model -> models.putIfAbsent(model.modelId(), model));
                embeddingModels.forEach(model -> models.put(model.modelId(), model));
                rerankModels.forEach(model -> models.put(model.modelId(), model));
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
        return openAiCompatibleLanguageModelProviderOptions(reasoningControlOptions,
            OpenAiThinkingOptions::applyEnableThinking);
    }

    @Override
    public RerankingModelProviderOptions rerankingModelProviderOptions() {
        return RerankingModelProviderOptions.builder()
            .providerOptionsSupported(true)
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

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank() || !value.endsWith("/")) {
            return value;
        }
        return value.substring(0, value.length() - 1);
    }
}
