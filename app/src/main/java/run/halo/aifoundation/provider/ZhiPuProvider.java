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
import run.halo.aifoundation.provider.support.openai.OpenAiThinkingOptions;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;

@Component
public class ZhiPuProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "https://open.bigmodel.cn/api/paas/v4";
    private static final String CHAT_PATH = "/chat/completions";
    private static final String EMBEDDING_PATH = "/embeddings";

    @Override
    public String getProviderType() {
        return "zhipuai";
    }

    @Override
    public String getDisplayName() {
        return "智谱开放平台";
    }

    @Override
    public String getDescription() {
        return "智谱 AI 推出的 GLM 系列大模型开放平台，支持对话、嵌入和多模态能力。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/zhipu.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://www.zhipu.com";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://open.bigmodel.cn/dev/api";
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
    public EmbeddingModelProviderOptions embeddingModelProviderOptions() {
        return new EmbeddingModelProviderOptions("openai", OpenAiEmbeddingOptionsFactory::build);
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions() {
        var reasoningControlOptions = ReasoningControlOptions.thinkingType();
        return openAiCompatibleLanguageModelProviderOptions(reasoningControlOptions,
            OpenAiThinkingOptions::applyThinkingType);
    }}
