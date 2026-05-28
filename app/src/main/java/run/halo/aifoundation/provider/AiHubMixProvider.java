package run.halo.aifoundation.provider;

import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.provider.support.OpenAiCompatibleEmbeddingModel;
import run.halo.aifoundation.provider.support.OpenAiEmbeddingOptionsFactory;

@Component
public class AiHubMixProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "https://aihubmix.com";
    private static final String COMPLETIONS_PATH = "/v1/chat/completions";
    private static final String EMBEDDINGS_PATH = "/v1/embeddings";
    private static final String APP_CODE = "NEUE3459";

    @Override
    public String getProviderType() {
        return "aihubmix";
    }

    @Override
    public String getDisplayName() {
        return "AIHubMix";
    }

    @Override
    public String getDescription() {
        return "AIHubMix 提供的多模型 API 聚合平台，支持 Claude、GPT、Gemini 等主流模型。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/aihubmix.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://aihubmix.com";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://docs.aihubmix.com";
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
        return new OpenAiCompatibleEmbeddingModel(openAiApi, modelId);
    }

    @Override
    protected void customizeDiscoveryRequest(WebClient.RequestHeadersSpec<?> requestSpec) {
        requestSpec.header("APP-Code", APP_CODE);
    }

    @Override
    public EmbeddingModelProviderOptions embeddingModelProviderOptions() {
        return new EmbeddingModelProviderOptions("openai", OpenAiEmbeddingOptionsFactory::build);
    }

    private OpenAiApi buildOpenAiApi(AiProvider provider, String apiKey) {
        var headers = new HttpHeaders();
        headers.set("APP-Code", APP_CODE);
        return OpenAiApi.builder()
            .baseUrl(resolveBaseUrl(provider))
            .apiKey(apiKey)
            .headers(headers)
            .completionsPath(COMPLETIONS_PATH)
            .embeddingsPath(EMBEDDINGS_PATH)
            .webClientBuilder(webClientBuilder(provider))
            .restClientBuilder(restClientBuilder(provider))
            .build();
    }
}
