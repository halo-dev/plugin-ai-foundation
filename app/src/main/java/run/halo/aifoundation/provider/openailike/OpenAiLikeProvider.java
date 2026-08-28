package run.halo.aifoundation.provider.openailike;

import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.AbstractAiProviderType;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ProviderImageGenerationClient;
import run.halo.aifoundation.provider.support.ProviderFeatureSets;
import run.halo.aifoundation.provider.support.ProviderRerankingClient;
import run.halo.aifoundation.provider.support.ProviderUris;
import run.halo.aifoundation.provider.support.RerankingModelProviderOptions;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;

@Component
public class OpenAiLikeProvider extends AbstractAiProviderType {

    private static final String COMPLETIONS_PATH = "/chat/completions";
    private static final String EMBEDDINGS_PATH = "/embeddings";
    private static final String RERANK_PATH = "/rerank";
    private static final String IMAGES_GENERATIONS_PATH = "/images/generations";

    @Override
    public String getProviderType() {
        return "openailike";
    }

    @Override
    public String getDisplayName() {
        return "OpenAI 兼容";
    }

    @Override
    public String getDescription() {
        return "用于配置 OpenAI 兼容的 AI 提供商，支持对话、嵌入和图像生成能力。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/openai.png";
    }

    @Override
    public boolean isBuiltIn() {
        return false;
    }

    @Override
    public boolean requiresBaseUrl() {
        return true;
    }

    @Nullable
    @Override
    public String getDefaultBaseUrl() {
        return null;
    }

    @Override
    public List<AdapterType> getSupportedAdapterTypes() {
        return List.of(AdapterType.OPENAI_CHAT, AdapterType.OPENAI_EMBEDDING,
            AdapterType.RERANK, AdapterType.OPENAI_IMAGE);
    }

    @Override
    public List<ModelFeature> getSupportedFeatures() {
        return ProviderFeatureSets.TEXT;
    }

    @Override
    public String getChatEndpointPath() {
        return COMPLETIONS_PATH;
    }

    @Override
    public String getEmbeddingEndpointPath() {
        return EMBEDDINGS_PATH;
    }

    @Override
    public String getRerankEndpointPath() {
        return RERANK_PATH;
    }

    @Override
    public String getImageEndpointPath() {
        return IMAGES_GENERATIONS_PATH;
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey, String modelId) {
        return new OpenAiCompatibleChatModel(chatCompletionsOptions(provider, apiKey, modelId,
            java.util.Map.of()), webClientBuilder(provider));
    }

    @Override
    public EmbeddingModel buildEmbeddingModel(AiProvider provider, String apiKey, String modelId) {
        var options = OpenAiCompatibleEmbeddingOptions.builder()
            .baseUrl(resolveBaseUrl(provider))
            .endpointPath(embeddingEndpointPath(provider))
            .apiKey(apiKey)
            .model(modelId)
            .build();
        return new OpenAiCompatibleEmbeddingModel(options, webClientBuilder(provider));
    }

    @Override
    public ProviderImageGenerationClient buildImageGenerationClient(AiProvider provider,
        String apiKey, String modelId) {
        return new OpenAiCompatibleImageGenerationClient(new OpenAiCompatibleImageOptions(
            getProviderType(), resolveBaseUrl(provider),
            imageEndpointPath(provider), apiKey, modelId, java.util.Map.of()),
            webClientBuilder(provider));
    }

    @Override
    public ProviderRerankingClient buildRerankingClient(AiProvider provider, String apiKey,
        String modelId) {
        return new OpenAiLikeRerankingClient(
            ProviderUris.withoutTrailingSlashes(resolveBaseUrl(provider)),
            rerankEndpointPath(provider), modelId, apiKey, webClientBuilder(provider));
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions() {
        var reasoningControlOptions = ReasoningControlOptions.unsupported();
        return chatCompletionsLanguageModelProviderOptions(reasoningControlOptions, null, true);
    }

    @Override
    public EmbeddingModelProviderOptions embeddingModelProviderOptions() {
        return new EmbeddingModelProviderOptions(OpenAiEmbeddingOptionsFactory::build);
    }

    @Override
    protected boolean supportsChatEndpointOverrides() {
        return true;
    }

    private String embeddingEndpointPath(AiProvider provider) {
        return endpointPath(provider.getSpec().getEmbeddingEndpointPath(),
            getEmbeddingEndpointPath(), EMBEDDINGS_PATH);
    }

    private String rerankEndpointPath(AiProvider provider) {
        return endpointPath(provider.getSpec().getRerankEndpointPath(),
            getRerankEndpointPath(), RERANK_PATH);
    }

    private String imageEndpointPath(AiProvider provider) {
        return endpointPath(provider.getSpec().getImageEndpointPath(),
            getImageEndpointPath(), IMAGES_GENERATIONS_PATH);
    }

    private String endpointPath(String configuredPath, String defaultPath, String fallbackPath) {
        var path = configuredPath;
        if (path == null || path.isBlank()) {
            path = defaultPath;
        }
        if (path == null || path.isBlank()) {
            path = fallbackPath;
        }
        return path.startsWith("/") ? path : "/" + path;
    }

}
