package run.halo.aifoundation.provider;

import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.ProviderImageGenerationClient;
import run.halo.aifoundation.provider.support.openai.OpenAiEmbeddingOptionsFactory;
import run.halo.aifoundation.provider.support.openai.OpenAiReasoningOptions;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;

@Component
public class OpenAiLikeProvider extends AbstractAiProviderType {

    private static final String COMPLETIONS_PATH = "/chat/completions";
    private static final String EMBEDDINGS_PATH = "/embeddings";

    @Override
    public String getProviderType() {
        return "openailike";
    }

    @Override
    public String getDisplayName() {
        return "OpenAI 兼容";
    }

    @Override
    public String getDescription() {
        return "用于配置 OpenAI 兼容的 AI 提供商，支持对话、嵌入和图像生成能力。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/openai.png";
    }

    @Override
    public boolean isBuiltIn() {
        return false;
    }

    @Override
    public boolean requiresBaseUrl() {
        return true;
    }

    @Nullable
    @Override
    public String getDefaultBaseUrl() {
        return null;
    }

    @Override
    public List<AdapterType> getSupportedAdapterTypes() {
        return List.of(AdapterType.OPENAI_CHAT, AdapterType.OPENAI_EMBEDDING,
            AdapterType.OPENAI_IMAGE);
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
    public ProviderImageGenerationClient buildImageGenerationClient(AiProvider provider,
        String apiKey, String modelId) {
        return buildOpenAiCompatibleImageGenerationClient(provider, apiKey, modelId);
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
    }
}
