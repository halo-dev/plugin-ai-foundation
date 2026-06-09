package run.halo.aifoundation.provider;

import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.AdapterType;

@Component
public class GiteeMoArkProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "https://ai.gitee.com/v1";
    private static final String COMPLETIONS_PATH = "/chat/completions";
    private static final String EMBEDDINGS_PATH = "/embeddings";

    @Override
    public String getProviderType() {
        return "gitee-moark";
    }

    @Override
    public String getDisplayName() {
        return "Gitee 模力方舟";
    }

    @Override
    public String getDescription() {
        return "Gitee 模力方舟提供兼容 OpenAI 风格的 Serverless API，支持文本生成和对话模型调用。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/gitee-moark.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://ai.gitee.com/";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://ai.gitee.com/docs/products/apis/texts/text-generation";
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
        return buildOpenAiCompatibleChatModel(provider, apiKey, modelId);
    }
}
