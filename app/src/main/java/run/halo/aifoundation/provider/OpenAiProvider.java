package run.halo.aifoundation.provider;

import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.openai.OpenAiEmbeddingOptionsFactory;
import run.halo.aifoundation.provider.support.openai.OpenAiReasoningOptions;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;

@Component
public class OpenAiProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String COMPLETIONS_PATH = "/chat/completions";
    private static final String EMBEDDINGS_PATH = "/embeddings";

    @Override
    public String getProviderType() {
        return "openai";
    }

    @Override
    public String getDisplayName() {
        return "OpenAI";
    }

    @Override
    public String getDescription() {
        return "OpenAI 推出的 GPT 系列大模型，支持对话、嵌入和多种任务。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/openai.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://openai.com";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://platform.openai.com/docs";
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
        return buildOpenAiCompatibleChatModel(provider, apiKey, modelId);
    }

    @Override
    public EmbeddingModel buildEmbeddingModel(AiProvider provider, String apiKey, String modelId) {
        return buildOpenAiCompatibleEmbeddingModel(provider, apiKey, modelId);
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions() {
        var reasoningControlOptions =
            ReasoningControlOptions.openAiCompatibleEffort(OpenAiReasoningOptions::applyEffort);
        return openAiCompatibleLanguageModelProviderOptions(reasoningControlOptions, null, true);
    }

    @Override
    public EmbeddingModelProviderOptions embeddingModelProviderOptions() {
        return new EmbeddingModelProviderOptions("openai", OpenAiEmbeddingOptionsFactory::build);
    }}
