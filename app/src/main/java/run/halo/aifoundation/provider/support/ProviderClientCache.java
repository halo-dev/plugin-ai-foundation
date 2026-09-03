package run.halo.aifoundation.provider.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.AiProviderType;

@Slf4j
@Component
public class ProviderClientCache {

    private final Map<String, ChatModel> chatModelCache = new ConcurrentHashMap<>();
    private final Map<String, EmbeddingModel> embeddingModelCache = new ConcurrentHashMap<>();
    private final Map<String, ProviderRerankingClient> rerankingClientCache =
        new ConcurrentHashMap<>();
    private final Map<String, ProviderImageGenerationClient> imageGenerationClientCache =
        new ConcurrentHashMap<>();
    private final ApplicationContext applicationContext;

    private volatile Map<String, AiProviderType> providerTypeMap;

    public ProviderClientCache(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public Map<String, AiProviderType> getProviderTypeMap() {
        if (providerTypeMap == null) {
            synchronized (this) {
                if (providerTypeMap == null) {
                    providerTypeMap = applicationContext.getBeansOfType(AiProviderType.class)
                        .values().stream()
                        .collect(java.util.stream.Collectors.toMap(
                            AiProviderType::getProviderType,
                            java.util.function.Function.identity(),
                            (a, b) -> {
                                log.warn("Duplicate provider type: {}, keeping first", a.getProviderType());
                                return a;
                            }
                        ));
                }
            }
        }
        return providerTypeMap;
    }

    public AiProviderType getProviderType(String providerType) {
        var type = getProviderTypeMap().get(providerType);
        if (type == null) {
            throw new IllegalArgumentException("Unsupported provider type: " + providerType);
        }
        return type;
    }

    public ChatModel getOrCreateChatModel(AiProvider provider, String apiKey,
        ProviderModelRef model) {
        var name = provider.getMetadata().getName();
        var type = getProviderType(provider.getSpec().getProviderType());
        var resolvedModel = ProviderModelResolver.resolve(type, model);
        var key = cacheKey(name, resolvedModel);
        return chatModelCache.computeIfAbsent(key, k -> {
            log.debug("Creating chat model for provider: {}, model: {}, adapter: {}", name,
                resolvedModel.modelId(), resolvedModel.adapterType());
            return type.buildChatModel(provider, apiKey, resolvedModel);
        });
    }

    public EmbeddingModel getOrCreateEmbeddingModel(AiProvider provider, String apiKey,
        ProviderModelRef model) {
        var name = provider.getMetadata().getName();
        var type = getProviderType(provider.getSpec().getProviderType());
        var resolvedModel = ProviderModelResolver.resolve(type, model);
        var key = cacheKey(name, resolvedModel);
        var existing = embeddingModelCache.get(key);
        if (existing != null) {
            return existing;
        }
        var embeddingModel = type.buildEmbeddingModel(provider, apiKey, resolvedModel);
        if (embeddingModel == null) {
            return null;
        }
        var prev = embeddingModelCache.putIfAbsent(key, embeddingModel);
        return prev != null ? prev : embeddingModel;
    }

    public ProviderRerankingClient getOrCreateRerankingClient(AiProvider provider, String apiKey,
        ProviderModelRef model) {
        var name = provider.getMetadata().getName();
        var type = getProviderType(provider.getSpec().getProviderType());
        var resolvedModel = ProviderModelResolver.resolve(type, model);
        var key = cacheKey(name, resolvedModel);
        var existing = rerankingClientCache.get(key);
        if (existing != null) {
            return existing;
        }
        var client = type.buildRerankingClient(provider, apiKey, resolvedModel);
        if (client == null) {
            return null;
        }
        var prev = rerankingClientCache.putIfAbsent(key, client);
        return prev != null ? prev : client;
    }

    public ProviderImageGenerationClient getOrCreateImageGenerationClient(AiProvider provider,
        String apiKey, ProviderModelRef model) {
        var name = provider.getMetadata().getName();
        var type = getProviderType(provider.getSpec().getProviderType());
        var resolvedModel = ProviderModelResolver.resolve(type, model);
        var key = cacheKey(name, resolvedModel);
        var existing = imageGenerationClientCache.get(key);
        if (existing != null) {
            return existing;
        }
        var client = type.buildImageGenerationClient(provider, apiKey, resolvedModel);
        if (client == null) {
            return null;
        }
        var prev = imageGenerationClientCache.putIfAbsent(key, client);
        return prev != null ? prev : client;
    }

    public void invalidate(String providerName) {
        var prefix = providerName + "/";
        chatModelCache.keySet().removeIf(key -> key.startsWith(prefix));
        embeddingModelCache.keySet().removeIf(key -> key.startsWith(prefix));
        rerankingClientCache.keySet().removeIf(key -> key.startsWith(prefix));
        imageGenerationClientCache.keySet().removeIf(key -> key.startsWith(prefix));
        log.debug("Invalidated cached models for provider: {}", providerName);
    }

    public void invalidateAll() {
        chatModelCache.clear();
        embeddingModelCache.clear();
        rerankingClientCache.clear();
        imageGenerationClientCache.clear();
        log.debug("Invalidated all cached provider models");
    }

    private String cacheKey(String providerName, ProviderModelRef model) {
        return providerName + "/" + model.modelType().getValue() + "/" + model.modelId() + "/"
            + model.cacheSegment();
    }
}
