package run.halo.aifoundation.provider;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.lang.Nullable;

public interface ProviderAdapter {

    ChatModel buildChatModel(String modelId);

    @Nullable
    EmbeddingModel buildEmbeddingModel(String modelId);

    int maxEmbeddingsPerCall();

    boolean supportsParallelCalls();

    String getProviderType();
}
