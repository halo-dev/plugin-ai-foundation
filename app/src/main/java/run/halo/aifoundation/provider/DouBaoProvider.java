package run.halo.aifoundation.provider;

import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.ProviderImageGenerationClient;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;
import run.halo.aifoundation.provider.support.image.ImageGenerationClientOptions;
import run.halo.aifoundation.provider.support.image.ModelArkImageGenerationClient;
import run.halo.aifoundation.provider.support.openai.OpenAiEmbeddingOptionsFactory;
import run.halo.aifoundation.provider.support.openai.OpenAiThinkingOptions;

@Component
public class DouBaoProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3";
    private static final String CHAT_PATH = "/chat/completions";
    private static final String EMBEDDING_PATH = "/embeddings";

    @Override
    public String getProviderType() {
        return "doubao";
    }

    @Override
    public String getDisplayName() {
        return "豆包";
    }

    @Override
    public String getDescription() {
        return "字节跳动推出的豆包大模型，提供对话、嵌入和多模态能力。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/doubao.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://www.doubao.com";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://www.volcengine.com/product/doubao/";
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
        return List.of(AdapterType.OPENAI_CHAT, AdapterType.OPENAI_EMBEDDING,
            AdapterType.DOUBAO_IMAGE);
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
    public ProviderImageGenerationClient buildImageGenerationClient(AiProvider provider,
        String apiKey, String modelId) {
        return new ModelArkImageGenerationClient(new ImageGenerationClientOptions(
            getProviderType(), resolveBaseUrl(provider), apiKey, modelId, null),
            webClientBuilder(provider));
    }

    @Override
    public EmbeddingModelProviderOptions embeddingModelProviderOptions() {
        return new EmbeddingModelProviderOptions("openai", OpenAiEmbeddingOptionsFactory::build);
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions() {
        var reasoningControlOptions = ReasoningControlOptions.thinkingType();
        return openAiCompatibleLanguageModelProviderOptions(reasoningControlOptions,
            OpenAiThinkingOptions::applyThinkingType);
    }

}
