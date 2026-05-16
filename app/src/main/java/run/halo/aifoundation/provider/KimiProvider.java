package run.halo.aifoundation.provider;

import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.extension.AiProvider;

@Component
public class KimiProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "https://api.moonshot.cn";
    private static final String COMPLETIONS_PATH = "/v1/chat/completions";
    private static final String EMBEDDINGS_PATH = "/v1/embeddings";

    @Override
    public String getProviderType() {
        return "kimi";
    }

    @Override
    public String getDisplayName() {
        return "月之暗面 Kimi";
    }

    @Override
    public String getDescription() {
        return "月之暗面（Moonshot AI）推出的智能助手，支持超长上下文和多模态理解。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/kimi.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://www.moonshot.cn";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://platform.kimi.com/docs/api/overview";
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
        return List.of("openai-chat");
    }

    @Override
    public boolean supportsEmbeddings() {
        return false;
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
    public ChatModel buildChatModel(AiProvider provider, String apiKey, String modelId) {
        var openAiApi = OpenAiApi.builder()
            .baseUrl(resolveBaseUrl(provider))
            .apiKey(apiKey)
            .completionsPath(COMPLETIONS_PATH)
            .embeddingsPath(EMBEDDINGS_PATH)
            .webClientBuilder(webClientBuilder(provider))
            .restClientBuilder(restClientBuilder(provider))
            .build();
        return OpenAiChatModel.builder()
            .openAiApi(openAiApi)
            .defaultOptions(OpenAiChatOptions.builder().model(modelId).build())
            .build();
    }
}
