package run.halo.aifoundation.provider;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.openai.OpenAiThinkingOptions;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;

@Component
public class KimiProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "https://api.moonshot.cn/v1";
    private static final String COMPLETIONS_PATH = "/chat/completions";
    private static final String EMBEDDINGS_PATH = "/embeddings";

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

    @Override
    public Mono<List<DiscoveredModel>> discoverModels(AiProvider provider, String apiKey) {
        return getDiscoveryJson(provider, apiKey,
            uriBuilder -> uriBuilder.path("/models").build(),
            this::customizeDiscoveryRequest
        ).map(json -> {
            var data = listValue(json, "data");
            if (data == null) {
                return List.<DiscoveredModel>of();
            }
            return discoveredModelsFromNodes(data, "id",
                node -> remoteDiscoveredModel(stringValue(node, "id"), ModelType.LANGUAGE,
                    languageFeatures(node), AdapterType.OPENAI_CHAT));
        });
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions() {
        var reasoningControlOptions = ReasoningControlOptions.thinkingType();
        return openAiCompatibleLanguageModelProviderOptions(reasoningControlOptions,
            OpenAiThinkingOptions::applyThinkingType);
    }

    private Set<ModelFeature> languageFeatures(java.util.Map<?, ?> node) {
        var features = new LinkedHashSet<ModelFeature>();
        features.add(ModelFeature.STREAMING);
        if (booleanValue(node, "supports_image_in")) {
            features.add(ModelFeature.VISION);
        }
        if (booleanValue(node, "supports_reasoning")) {
            features.add(ModelFeature.REASONING);
        }
        return Set.copyOf(features);
    }
}
