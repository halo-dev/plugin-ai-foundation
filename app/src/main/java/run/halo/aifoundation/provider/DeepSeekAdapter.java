package run.halo.aifoundation.provider;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import run.halo.aifoundation.extension.AiProvider;

public class DeepSeekAdapter extends AbstractProviderAdapter {

    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";

    private final DeepSeekApi deepSeekApi;

    public DeepSeekAdapter(AiProvider provider, String apiKey) {
        super(provider, apiKey);
        this.deepSeekApi = DeepSeekApi.builder()
            .baseUrl(resolveBaseUrl(DEFAULT_BASE_URL))
            .apiKey(apiKey)
            .webClientBuilder(webClientBuilder())
            .restClientBuilder(restClientBuilder())
            .build();
    }

    @Override
    public ChatModel buildChatModel(String modelId) {
        return DeepSeekChatModel.builder()
            .deepSeekApi(deepSeekApi)
            .defaultOptions(DeepSeekChatOptions.builder().model(modelId).build())
            .build();
    }

    @Override
    public org.springframework.ai.embedding.EmbeddingModel buildEmbeddingModel(String modelId) {
        return null; // DeepSeek does not support embeddings
    }

    @Override
    public int maxEmbeddingsPerCall() {
        return 0;
    }

    @Override
    public boolean supportsParallelCalls() {
        return false;
    }

    @Override
    public String getProviderType() {
        return "deepseek";
    }

    @Override
    protected String getDefaultBaseUrl() {
        return DEFAULT_BASE_URL;
    }
}
