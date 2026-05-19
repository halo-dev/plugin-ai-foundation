package run.halo.aifoundation.provider;

import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.AdapterType;

@Component
public class DouBaoProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "https://ark.cn-beijing.volces.com/api";
    private static final String CHAT_PATH = "/v3/chat/completions";
    private static final String EMBEDDING_PATH = "/v3/embeddings";

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
        return List.of(AdapterType.OPENAI_CHAT, AdapterType.OPENAI_EMBEDDING);
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
        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED,
            OpenAiEmbeddingOptions.builder().model(modelId).build());
    }

    private OpenAiApi buildOpenAiApi(AiProvider provider, String apiKey) {
        return OpenAiApi.builder()
            .baseUrl(resolveBaseUrl(provider))
            .apiKey(apiKey)
            .completionsPath(CHAT_PATH)
            .embeddingsPath(EMBEDDING_PATH)
            .webClientBuilder(webClientBuilder(provider))
            .restClientBuilder(restClientBuilder(provider))
            .build();
    }
}
