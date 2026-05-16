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
public class ZhiPuProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "https://open.bigmodel.cn/api";
    private static final String CHAT_PATH = "/paas/v4/chat/completions";
    private static final String EMBEDDING_PATH = "/paas/v4/embeddings";

    @Override
    public String getProviderType() {
        return "zhipuai";
    }

    @Override
    public String getDisplayName() {
        return "智谱开放平台";
    }

    @Override
    public String getDescription() {
        return "智谱 AI 推出的 GLM 系列大模型开放平台，支持对话、嵌入和多模态能力。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/zhipu.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://www.zhipu.com";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://open.bigmodel.cn/dev/api";
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
            .webClientBuilder(webClientBuilder(provider))
            .restClientBuilder(restClientBuilder(provider))
            .build();
    }
}
