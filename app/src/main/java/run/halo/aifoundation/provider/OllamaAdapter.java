package run.halo.aifoundation.provider;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import run.halo.aifoundation.extension.AiProvider;

public class OllamaAdapter extends AbstractProviderAdapter {

    private static final String DEFAULT_BASE_URL = "http://localhost:11434";

    private final OllamaApi ollamaApi;

    public OllamaAdapter(AiProvider provider, String apiKey) {
        super(provider, apiKey);
        this.ollamaApi = OllamaApi.builder()
            .baseUrl(resolveBaseUrl(DEFAULT_BASE_URL))
            .webClientBuilder(webClientBuilder())
            .restClientBuilder(restClientBuilder())
            .build();
    }

    @Override
    public ChatModel buildChatModel(String modelId) {
        return OllamaChatModel.builder()
            .ollamaApi(ollamaApi)
            .defaultOptions(OllamaChatOptions.builder().model(modelId).build())
            .modelManagementOptions(ModelManagementOptions.defaults())
            .build();
    }

    @Override
    public EmbeddingModel buildEmbeddingModel(String modelId) {
        return OllamaEmbeddingModel.builder()
            .ollamaApi(ollamaApi)
            .defaultOptions(OllamaEmbeddingOptions.builder().model(modelId).build())
            .modelManagementOptions(ModelManagementOptions.defaults())
            .build();
    }

    @Override
    public int maxEmbeddingsPerCall() {
        return 1;
    }

    @Override
    public boolean supportsParallelCalls() {
        return false;
    }

    @Override
    public String getProviderType() {
        return "ollama";
    }
}
