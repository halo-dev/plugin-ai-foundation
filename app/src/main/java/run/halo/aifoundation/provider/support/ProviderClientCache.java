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

    public ChatModel getOrCreateChatModel(AiProvider provider, String apiKey, String modelId) {
        var name = provider.getMetadata().getName();
        var key = name + "/" + modelId;
        return chatModelCache.computeIfAbsent(key, k -> {
            log.debug("Creating chat model for provider: {}, model: {}", name, modelId);
            var type = getProviderType(provider.getSpec().getProviderType());
            return type.buildChatModel(provider, apiKey, modelId);
        });
    }

    public EmbeddingModel getOrCreateEmbeddingModel(AiProvider provider, String apiKey, String modelId) {
        var name = provider.getMetadata().getName();
        var key = name + "/" + modelId;
        return embeddingModelCache.computeIfAbsent(key, k -> {
            log.debug("Creating embedding model for provider: {}, model: {}", name, modelId);
            var type = getProviderType(provider.getSpec().getProviderType());
            return type.buildEmbeddingModel(provider, apiKey, modelId);
        });
    }

    public void invalidate(String providerName) {
        var prefix = providerName + "/";
        chatModelCache.keySet().removeIf(key -> key.startsWith(prefix));
        embeddingModelCache.keySet().removeIf(key -> key.startsWith(prefix));
        log.debug("Invalidated cached models for provider: {}", providerName);
    }

    public void invalidateAll() {
        chatModelCache.clear();
        embeddingModelCache.clear();
        log.debug("Invalidated all cached provider models");
    }
}
