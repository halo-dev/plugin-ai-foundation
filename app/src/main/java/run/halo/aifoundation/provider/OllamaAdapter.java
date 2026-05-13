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
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.extension.AiProvider;

@Slf4j
public class OllamaAdapter extends AbstractProviderAdapter {

    private static final String DEFAULT_BASE_URL = "http://localhost:11434";

    private final OllamaApi ollamaApi;

    public OllamaAdapter(AiProvider provider, String apiKey) {
        super(provider, apiKey);
        this.ollamaApi = OllamaApi.builder()
            .baseUrl(resolveBaseUrl(DEFAULT_BASE_URL))
            .webClientBuilder(webClientBuilder())
            .restClientBuilder(restClientBuilder())
            .build();
    }

    @Override
    public ChatModel buildChatModel(String modelId) {
        return OllamaChatModel.builder()
            .ollamaApi(ollamaApi)
            .defaultOptions(OllamaChatOptions.builder().model(modelId).build())
            .modelManagementOptions(ModelManagementOptions.defaults())
            .build();
    }

    @Override
    public EmbeddingModel buildEmbeddingModel(String modelId) {
        return OllamaEmbeddingModel.builder()
            .ollamaApi(ollamaApi)
            .defaultOptions(OllamaEmbeddingOptions.builder().model(modelId).build())
            .modelManagementOptions(ModelManagementOptions.defaults())
            .build();
    }

    @Override
    public Mono<List<DiscoveredModel>> discoverModels() {
        var baseUrl = resolveBaseUrl(DEFAULT_BASE_URL);
        var providerName = provider.getMetadata().getName();
        log.info("Discovering models for Ollama provider {}: baseUrl={}", providerName, baseUrl);

        var wc = webClientBuilder().baseUrl(baseUrl).build();
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
                            models.add(new DiscoveredModel(
                                modelId, modelId, inferCapabilities(modelId)));
                        }
                    }
                }
                log.info("Discovered {} models for Ollama provider {}", models.size(), providerName);
                return Mono.just(models);
            });
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
    public String getProviderType() {
        return "ollama";
    }

    @Override
    protected String getDefaultBaseUrl() {
        return DEFAULT_BASE_URL;
    }
}
