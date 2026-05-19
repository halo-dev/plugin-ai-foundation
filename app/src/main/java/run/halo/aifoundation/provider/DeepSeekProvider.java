package run.halo.aifoundation.provider;

import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.AdapterType;

@Component
public class DeepSeekProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";

    @Override
    public String getProviderType() {
        return "deepseek";
    }

    @Override
    public String getDisplayName() {
        return "深度求索 DeepSeek";
    }

    @Override
    public String getDescription() {
        return "深度求索推出的高性能大语言模型，支持对话、推理和代码生成。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/deepseek.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://deepseek.com";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://api-docs.deepseek.com";
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
        return List.of(AdapterType.OPENAI_CHAT);
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
        var deepSeekApi = DeepSeekApi.builder()
            .baseUrl(resolveBaseUrl(provider))
            .apiKey(apiKey)
            .webClientBuilder(webClientBuilder(provider))
            .restClientBuilder(restClientBuilder(provider))
            .build();
        return DeepSeekChatModel.builder()
            .deepSeekApi(deepSeekApi)
            .defaultOptions(DeepSeekChatOptions.builder().model(modelId).build())
            .build();
    }
}
