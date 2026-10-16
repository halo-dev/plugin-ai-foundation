package run.halo.aifoundation.provider.tokenhub;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.AbstractAiProviderType;
import run.halo.aifoundation.provider.mapping.DefaultParameterMapping;
import run.halo.aifoundation.provider.mapping.ModelParameter;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;
import run.halo.aifoundation.provider.support.StructuredOutputSupport;

/** Tencent Cloud TokenHub's OpenAI-compatible Chat Completions preset. */
@Component
public class TokenHubProvider extends AbstractAiProviderType {

    @Override
    public String getProviderType() {
        return "tokenhub";
    }

    @Override
    public String getDisplayName() {
        return "腾讯云 TokenHub";
    }

    @Override
    public String getDescription() {
        return "腾讯云大模型服务平台，使用 OpenAI 兼容对话接口；请手动填写对话模型 ID，并按该模型的实际能力选择工具调用等特性。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/tokenhub.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://cloud.tencent.com/document/product/1823";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://cloud.tencent.com/document/product/1823/130058";
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
        return "https://tokenhub.tencentmaas.com/v1";
    }

    @Override
    public List<AdapterType> getSupportedAdapterTypes() {
        return List.of(AdapterType.TOKENHUB_CHAT);
    }

    @Override
    public List<ModelFeature> getSupportedFeatures() {
        // Adapter capabilities permit explicit per-model configuration, not catalog-wide claims.
        return List.of(ModelFeature.STREAMING, ModelFeature.TOOL_CALL);
    }

    @Override
    public int maxEmbeddingsPerCall() {
        return 0;
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey, String modelId) {
        return new TokenHubChatModel(chatCompletionsOptions(provider, apiKey, modelId, Map.of()),
            webClientBuilder(provider));
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions() {
        var reasoning = ReasoningControlOptions.unsupported();
        var structuredOutput = StructuredOutputSupport.PROMPT_ONLY;
        var factory = chatCompletionsOptionsFactory(reasoning, false, structuredOutput);
        return chatCompletionsProviderOptionsBuilder()
            .requestHeadersSupported(true)
            .seedSupported(true)
            .nativeStrictToolSchemas(false)
            .structuredOutputSupport(structuredOutput)
            .chatOptionsFactory(factory::basic)
            .toolCallingChatOptionsFactory(factory::toolCalling)
            .structuredOutputChatOptionsFactory(factory::structured)
            .reasoningControlOptions(reasoning)
            .build();
    }

    @Override
    public Mono<List<DiscoveredModel>> discoverModels(AiProvider provider, String apiKey) {
        // TokenHub's identifier-only catalog also includes image/video/embedding models.
        // Without structured modality metadata, do not classify them as chat models by name.
        return Mono.just(List.of());
    }

    @Override
    public Map<ModelParameter, DefaultParameterMapping> getDefaultParameterMappings() {
        var defaults = new EnumMap<ModelParameter, DefaultParameterMapping>(ModelParameter.class);
        defaults.putAll(super.getDefaultParameterMappings());
        // Do not assume every hosted model supports non-portable sampling controls.
        for (var parameter : List.of(ModelParameter.TOP_K, ModelParameter.MIN_P,
            ModelParameter.REPETITION_PENALTY)) {
            defaults.put(parameter, DefaultParameterMapping.unsupported());
        }
        return Map.copyOf(defaults);
    }
}
