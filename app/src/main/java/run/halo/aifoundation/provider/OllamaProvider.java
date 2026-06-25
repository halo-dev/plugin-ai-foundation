package run.halo.aifoundation.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import org.springframework.ai.tool.ToolCallback;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.ProviderImageGenerationClient;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;
import run.halo.aifoundation.provider.support.openai.OpenAiCompatibleImageGenerationClient;
import run.halo.aifoundation.provider.support.openai.OpenAiCompatibleImageOptions;

@Slf4j
@Component
public class OllamaProvider extends AbstractAiProviderType {

    private static final String DEFAULT_BASE_URL = "http://localhost:11434";
    private static final String COMPLETIONS_PATH = "/api/chat";

    @Override
    public String getProviderType() {
        return "ollama";
    }

    @Override
    public String getDisplayName() {
        return "Ollama";
    }

    @Override
    public String getDescription() {
        return "本地运行开源大语言模型的工具，支持多种模型和自定义配置。";
    }

    @Override
    public String getIconUrl() {
        return "/plugins/ai-foundation/assets/static/brands/ollama.png";
    }

    @Override
    public String getWebsiteUrl() {
        return "https://ollama.com";
    }

    @Override
    public String getDocumentationUrl() {
        return "https://docs.ollama.com";
    }

    @Override
    public boolean isBuiltIn() {
        return false;
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
    public String getCompletionsPath() {
        return COMPLETIONS_PATH;
    }

    @Override
    public List<AdapterType> getSupportedAdapterTypes() {
        return List.of(AdapterType.OLLAMA_CHAT, AdapterType.OLLAMA_EMBEDDING,
            AdapterType.OPENAI_IMAGE);
    }

    @Override
    public int maxEmbeddingsPerCall() {
        return 1;
    }

    @Override
    public boolean supportsParallelCalls() {
        return false;
    }

    @Override
    public ChatModel buildChatModel(AiProvider provider, String apiKey, String modelId) {
        var ollamaApi = buildOllamaApi(provider);
        return OllamaChatModel.builder()
            .ollamaApi(ollamaApi)
            .options(OllamaChatOptions.builder().model(modelId).build())
            .modelManagementOptions(ModelManagementOptions.defaults())
            .build();
    }

    @Override
    public LanguageModelProviderOptions languageModelProviderOptions() {
        var reasoningControlOptions = ReasoningControlOptions.ollama();
        return LanguageModelProviderOptions.builder()
            .seedSupported(true)
            .chatOptionsFactory(this::buildChatOptions)
            .toolCallingChatOptionsFactory(this::buildToolCallingChatOptions)
            .structuredOutputChatOptionsFactory(this::buildChatOptions)
            .reasoningControlOptions(reasoningControlOptions)
            .build();
    }

    @Override
    public EmbeddingModel buildEmbeddingModel(AiProvider provider, String apiKey, String modelId) {
        var ollamaApi = buildOllamaApi(provider);
        return OllamaEmbeddingModel.builder()
            .ollamaApi(ollamaApi)
            .options(OllamaEmbeddingOptions.builder().model(modelId).build())
            .modelManagementOptions(ModelManagementOptions.defaults())
            .build();
    }

    @Override
    public ProviderImageGenerationClient buildImageGenerationClient(AiProvider provider,
        String apiKey, String modelId) {
        return new OpenAiCompatibleImageGenerationClient(
            new OpenAiCompatibleImageOptions(getProviderType(), openAiCompatibleBaseUrl(provider),
                apiKey, modelId, null),
            webClientBuilder(provider));
    }

    @Override
    public Mono<List<DiscoveredModel>> discoverModels(AiProvider provider, String apiKey) {
        var baseUrl = resolveBaseUrl(provider);
        var providerName = provider.getMetadata().getName();
        log.info("Discovering models for Ollama provider {}: baseUrl={}", providerName, baseUrl);

        var wc = webClientBuilder(provider).baseUrl(baseUrl).build();
        return wc.get()
            .uri("/api/tags")
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .flatMap(json -> {
                var modelsObj = json.get("models");
                if (!(modelsObj instanceof List<?> modelsList)) {
                    log.warn("Ollama API response missing 'models' array for {}", providerName);
                    return Mono.just(List.<DiscoveredModel>of());
                }
                List<DiscoveredModel> models = new ArrayList<>();
                for (var item : modelsList) {
                    if (item instanceof Map<?, ?> node) {
                        var nameObj = node.get("name");
                        var modelId = nameObj != null ? nameObj.toString() : "";
                        if (!modelId.isBlank()) {
                            models.add(inferModelProfile(modelId));
                        }
                    }
                }
                log.info("Discovered {} models for Ollama provider {}", models.size(), providerName);
                return Mono.just(models);
            });
    }

    private OllamaApi buildOllamaApi(AiProvider provider) {
        return OllamaApi.builder()
            .baseUrl(resolveBaseUrl(provider))
            .webClientBuilder(webClientBuilder(provider))
            .restClientBuilder(restClientBuilder(provider))
            .build();
    }

    private String openAiCompatibleBaseUrl(AiProvider provider) {
        var baseUrl = trimTrailingSlash(resolveBaseUrl(provider));
        return baseUrl.endsWith("/v1") ? baseUrl : baseUrl + "/v1";
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank() || !value.endsWith("/")) {
            return value;
        }
        return value.substring(0, value.length() - 1);
    }

    private OllamaChatOptions buildChatOptions(GenerateTextRequest request) {
        var builder = baseChatOptionsBuilder(request);
        applyReasoning(builder, request);
        return builder.build();
    }

    private OllamaChatOptions buildToolCallingChatOptions(GenerateTextRequest request,
        List<ToolCallback> toolCallbacks, java.util.Set<String> toolNames) {
        var builder = baseChatOptionsBuilder(request)
            .toolCallbacks(toolCallbacks);
        applyReasoning(builder, request);
        return builder.build();
    }

    private OllamaChatOptions.Builder baseChatOptionsBuilder(GenerateTextRequest request) {
        return OllamaChatOptions.builder()
            .temperature(request.getTemperature())
            .numPredict(request.getMaxOutputTokens())
            .topP(request.getTopP())
            .topK(request.getTopK())
            .presencePenalty(request.getPresencePenalty())
            .frequencyPenalty(request.getFrequencyPenalty())
            .seed(request.getSeed())
            .stop(request.getStopSequences());
    }

    private void applyReasoning(OllamaChatOptions.Builder builder, GenerateTextRequest request) {
        var reasoning = request.getReasoning();
        if (reasoning == null || !reasoning.isExplicit()) {
            return;
        }
        if (reasoning.getEffort() != null) {
            switch (reasoning.getEffort()) {
                case LOW -> builder.thinkLow();
                case MEDIUM -> builder.thinkMedium();
                case HIGH -> builder.thinkHigh();
            }
            return;
        }
        switch (reasoning.getMode()) {
            case ENABLED -> builder.enableThinking();
            case DISABLED -> builder.disableThinking();
            default -> {
            }
        }
    }
}
