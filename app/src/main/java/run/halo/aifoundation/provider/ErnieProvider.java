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

@Component
public class ErnieProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "https://qianfan.baidubce.com";
    private static final String CHAT_PATH = "/v2/chat/completions";
    private static final String EMBEDDING_PATH = "/v2/embeddings";

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
    public List<String> getSupportedEndpointTypes() {
        return List.of("openai-chat", "openai-embedding");
    }

    @Override
    public boolean supportsEmbeddings() {
        return true;
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
            .webClientBuilder(webClientBuilder())
            .restClientBuilder(restClientBuilder())
            .build();
    }
}
