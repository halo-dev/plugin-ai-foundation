package run.halo.aifoundation.provider;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import run.halo.aifoundation.extension.AiProvider;

public class ZhiPuAdapter extends AbstractProviderAdapter {

    private static final String DEFAULT_BASE_URL = "https://open.bigmodel.cn/api";
    private static final String CHAT_PATH = "/paas/v4/chat/completions";
    private static final String EMBEDDING_PATH = "/paas/v4/embeddings";

    private final OpenAiApi openAiApi;

    public ZhiPuAdapter(AiProvider provider, String apiKey) {
        super(provider, apiKey);
        this.openAiApi = OpenAiApi.builder()
            .baseUrl(resolveBaseUrl(DEFAULT_BASE_URL))
            .apiKey(apiKey)
            .completionsPath(CHAT_PATH)
            .embeddingsPath(EMBEDDING_PATH)
            .webClientBuilder(webClientBuilder())
            .restClientBuilder(restClientBuilder())
            .build();
    }

    @Override
    public ChatModel buildChatModel(String modelId) {
        return OpenAiChatModel.builder()
            .openAiApi(openAiApi)
            .defaultOptions(OpenAiChatOptions.builder().model(modelId).build())
            .build();
    }

    @Override
    public EmbeddingModel buildEmbeddingModel(String modelId) {
        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED,
            OpenAiEmbeddingOptions.builder().model(modelId).build());
    }

    @Override
    public int maxEmbeddingsPerCall() {
        return 96;
    }

    @Override
    public boolean supportsParallelCalls() {
        return true;
    }

    @Override
    public String getProviderType() {
        return "zhipuai";
    }
}
