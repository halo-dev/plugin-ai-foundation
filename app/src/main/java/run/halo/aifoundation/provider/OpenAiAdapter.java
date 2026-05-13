package run.halo.aifoundation.provider;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import run.halo.aifoundation.extension.AiProvider;

public class OpenAiAdapter extends AbstractProviderAdapter {

    private static final String DEFAULT_BASE_URL = "https://api.openai.com";
    private static final String COMPLETIONS_PATH = "/v1/chat/completions";
    private static final String EMBEDDINGS_PATH = "/v1/embeddings";

    private final OpenAiApi openAiApi;

    public OpenAiAdapter(AiProvider provider, String apiKey) {
        super(provider, apiKey);
        this.openAiApi = OpenAiApi.builder()
            .baseUrl(resolveBaseUrl(DEFAULT_BASE_URL))
            .apiKey(apiKey)
            .completionsPath(COMPLETIONS_PATH)
            .embeddingsPath(EMBEDDINGS_PATH)
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
        return new OpenAiEmbeddingModel(openAiApi,
            org.springframework.ai.document.MetadataMode.EMBED,
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
        return "openai";
    }
}
