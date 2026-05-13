package run.halo.aifoundation.provider;

import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Mono;

public interface ProviderAdapter {

    ChatModel buildChatModel(String modelId);

    @Nullable
    EmbeddingModel buildEmbeddingModel(String modelId);

    Mono<List<DiscoveredModel>> discoverModels();

    int maxEmbeddingsPerCall();

    boolean supportsParallelCalls();

    String getProviderType();
}
