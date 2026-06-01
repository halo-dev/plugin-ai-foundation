package run.halo.aifoundation.provider;

import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.OpenAiChatOptionsSupport;
import run.halo.aifoundation.provider.support.OpenAiThinkingOptions;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;

@Component
public class XiaomiMiMoProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "https://api.xiaomimimo.com";
    private static final String COMPLETIONS_PATH = "/v1/chat/completions";
    private static final String EMBEDDINGS_PATH = "/v1/embeddings";

    @Override
    public String getProviderType() {
        return "mimo";
    }

    @Override
    public String getDisplayName() {
        return "Xiaomi MiMo";
    }

    @Override
    public String getDescription() {
        return "小米推出的大模型服务，提供 MiMo 系列对话、多模态和长上下文模型。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/xiaomimimo.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://platform.xiaomimimo.com/";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://platform.xiaomimimo.com/#/docs/welcome";
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
}
