package run.halo.aifoundation.provider;

import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.OpenAiChatOptionsSupport;
import run.halo.aifoundation.provider.support.OpenAiCompatibleEmbeddingModel;
import run.halo.aifoundation.provider.support.OpenAiEmbeddingOptionsFactory;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;

@Component
public class DashScopeProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private static final String COMPLETIONS_PATH = "/v1/chat/completions";
    private static final String EMBEDDINGS_PATH = "/v1/embeddings";

    @Override
    public String getProviderType() {
        return "dashscope";
    }

    @Override
    public String getDisplayName() {
        return "阿里云百炼";
    }

    @Override
    public String getDescription() {
        return "阿里云百炼大模型服务平台，兼容 OpenAI 协议，支持通义系列等模型。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/dashscope.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://bailian.aliyun.com";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://bailian.console.aliyun.com/";
    }

    @Override
    public boolean isBuiltIn() {
        return true;
    }

    @Override
    public boolean requiresBaseUrl() {
        return true;
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
    public LanguageModelProviderOptions languageModelProviderOptions() {
        var reasoningControlOptions = ReasoningControlOptions.unsupported();
        return new LanguageModelProviderOptions(false, false,
            request -> OpenAiChatOptionsSupport.buildBasic(request, getProviderType(),
                reasoningControlOptions, null),
            (request, toolCallbacks, toolNames) -> OpenAiChatOptionsSupport.buildToolCalling(
                request, toolCallbacks, toolNames, getProviderType(), reasoningControlOptions,
                null, true),
            request -> OpenAiChatOptionsSupport.buildStructured(request, getProviderType(),
                reasoningControlOptions, null),
            reasoningControlOptions);
    }

    @Override
    public EmbeddingModelProviderOptions embeddingModelProviderOptions() {
        return new EmbeddingModelProviderOptions("openai", OpenAiEmbeddingOptionsFactory::build);
    }

    private OpenAiApi buildOpenAiApi(AiProvider provider, String apiKey) {
        return OpenAiApi.builder()
            .baseUrl(resolveBaseUrl(provider))
            .apiKey(apiKey)
            .completionsPath(COMPLETIONS_PATH)
            .embeddingsPath(EMBEDDINGS_PATH)
            .webClientBuilder(webClientBuilder(provider))
            .restClientBuilder(restClientBuilder(provider))
            .build();
    }
}
