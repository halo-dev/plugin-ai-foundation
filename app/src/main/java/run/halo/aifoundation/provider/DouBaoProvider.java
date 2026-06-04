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
import run.halo.aifoundation.provider.support.OpenAiThinkingOptions;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;

@Component
public class DouBaoProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3";
    private static final String CHAT_PATH = "/chat/completions";
    private static final String EMBEDDING_PATH = "/embeddings";

    @Override
    public String getProviderType() {
        return "doubao";
    }

    @Override
    public String getDisplayName() {
        return "豆包";
    }

    @Override
    public String getDescription() {
        return "字节跳动推出的豆包大模型，提供对话、嵌入和多模态能力。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/doubao.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://www.doubao.com";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://www.volcengine.com/product/doubao/";
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
    public EmbeddingModelProviderOptions embeddingModelProviderOptions() {
        return new EmbeddingModelProviderOptions("openai", OpenAiEmbeddingOptionsFactory::build);
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions() {
        var reasoningControlOptions = ReasoningControlOptions.thinkingType();
        return new LanguageModelProviderOptions(false, false,
            request -> OpenAiChatOptionsSupport.buildBasic(request, getProviderType(),
                reasoningControlOptions, OpenAiThinkingOptions::applyThinkingType),
            (request, toolCallbacks, toolNames) -> OpenAiChatOptionsSupport.buildToolCalling(
                request, toolCallbacks, toolNames, getProviderType(), reasoningControlOptions,
                OpenAiThinkingOptions::applyThinkingType),
            request -> OpenAiChatOptionsSupport.buildStructured(request, getProviderType(),
                reasoningControlOptions, OpenAiThinkingOptions::applyThinkingType),
            reasoningControlOptions);
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
