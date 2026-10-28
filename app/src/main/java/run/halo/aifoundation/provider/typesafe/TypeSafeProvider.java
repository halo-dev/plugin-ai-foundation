package run.halo.aifoundation.provider.typesafe;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.capability.DecisionCapability;
import run.halo.aifoundation.decision.DecisionQuestionType;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.AbstractAiProviderType;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderDecisionClient;
import run.halo.aifoundation.provider.support.ProviderModelRef;
import run.halo.aifoundation.provider.transport.ProviderDiagnostics;
import run.halo.aifoundation.provider.transport.ProviderHttpResponseSupport;

@Component
public class TypeSafeProvider extends AbstractAiProviderType {
    @Override
    public DecisionCapability decisionCapabilities() {
        return DecisionCapability.builder()
            .questionTypes(List.of(DecisionQuestionType.YES_NO, DecisionQuestionType.CHOICE,
                DecisionQuestionType.SCORE))
            .structuredState(true).maxChoiceOptions(255).maxScoreLevels(10)
            .choiceProbabilities(true).choiceConfidence(true)
            .scoreProbabilities(true).scoreConfidence(true).scoreLegend(true)
            .build();
    }

    @Override
    public String getProviderType() {
        return "typesafe";
    }

    @Override
    public String getDisplayName() {
        return "TypeSafe (Jev)";
    }

    @Override
    public String getDescription() {
        return "Jev 决策模型，支持文本与 JSON 状态的概率判断、单选和评分。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/typesafe.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://typesafe.ai";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://docs.typesafe.ai/api";
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
        return "https://api.typesafe.ai";
    }

    @Override
    public String getCompletionsPath() {
        return null;
    }

    @Override
    public List<AdapterType> getSupportedAdapterTypes() {
        return List.of(AdapterType.TYPESAFE_SYSTEM_ONE);
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey, String modelId) {
        throw new UnsupportedOperationException("TypeSafe provides decision models");
    }

    @Override
    public ProviderDecisionClient buildDecisionClient(AiProvider provider, String apiKey,
        ProviderModelRef model) {
        var resolved = resolveModel(model);
        validateNativeModelOptions(resolved);
        return new TypeSafeDecisionClient(resolveBaseUrl(provider), apiKey, resolved.modelId(),
            discoveryWebClientBuilder(provider));
    }

    @Override
    public void validateNativeModelOptions(ProviderModelRef model) {
        if (model.nativeOptions() != null && !model.nativeOptions().isEmpty()) {
            throw new IllegalArgumentException("TypeSafe decision models do not accept native options");
        }
    }

    @Override
    public Mono<List<DiscoveredModel>> discoverModels(AiProvider provider, String apiKey) {
        var endpoint = resolveBaseUrl(provider).replaceAll("/+$", "") + "/v1/models";
        var diagnostics = ProviderDiagnostics.create(getProviderType(), "models");
        return discoveryWebClientBuilder(provider).build().get().uri(endpoint)
            .headers(headers -> headers.setBearerAuth(apiKey))
            .exchangeToMono(response -> {
                if (!response.statusCode().is2xxSuccessful()) {
                    return ProviderHttpResponseSupport.errorMono(response, getProviderType(),
                        "models", diagnostics);
                }
                return response.bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() { });
            })
            .switchIfEmpty(Mono.error(new IllegalStateException("Empty TypeSafe model catalog")))
            .map(body -> {
                if (!(body.get("models") instanceof List<?> models)) {
                    throw new IllegalStateException("TypeSafe model catalog is missing models");
                }
                return models.stream().map(entry -> {
                    if (!(entry instanceof Map<?, ?> item)
                        || !(item.get("name") instanceof String name) || name.isBlank()) {
                        throw new IllegalStateException("Invalid TypeSafe model catalog entry");
                    }
                    return new DiscoveredModel(name, name, ModelType.DECISION, Set.of(),
                        AdapterType.TYPESAFE_SYSTEM_ONE, DiscoverySource.REMOTE, DiscoveryConfidence.HIGH);
                }).toList();
            });
    }
}
