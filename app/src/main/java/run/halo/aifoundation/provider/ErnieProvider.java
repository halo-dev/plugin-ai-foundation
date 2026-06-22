package run.halo.aifoundation.provider;

import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.ProviderRerankingClient;
import run.halo.aifoundation.provider.support.openai.OpenAiEmbeddingOptionsFactory;
import run.halo.aifoundation.provider.support.openai.OpenAiThinkingOptions;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;
import run.halo.aifoundation.provider.support.RerankingModelProviderOptions;
import run.halo.aifoundation.provider.support.rerank.StandardRerankingClient;

@Component
public class ErnieProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "https://qianfan.baidubce.com/v2";
    private static final String CHAT_PATH = "/chat/completions";
    private static final String EMBEDDING_PATH = "/embeddings";

    @Override
    public String getProviderType() {
        return "ernie";
    }

    @Override
    public String getDisplayName() {
        return "文心一言";
    }

    @Override
    public String getDescription() {
        return "百度推出的文心大模型，具备知识增强和中文理解能力。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/wenxin.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://yiyan.baidu.com";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://cloud.baidu.com/doc/WENXINWORKSHOP/index.html";
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
        return new StandardRerankingClient(getProviderType(), trimTrailingSlash(resolveBaseUrl(provider)),
            "/rerank", modelId, apiKey, webClientBuilder(provider));
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

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank() || !value.endsWith("/")) {
            return value;
        }
        return value.substring(0, value.length() - 1);
    }
}
