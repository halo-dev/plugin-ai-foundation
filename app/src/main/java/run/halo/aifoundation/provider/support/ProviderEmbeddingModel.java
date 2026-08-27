package run.halo.aifoundation.provider.support;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * Embedding model extension for provider-native requests that cannot be represented by Spring
 * AI's text-only embedding request.
 */
public interface ProviderEmbeddingModel extends EmbeddingModel {

    EmbeddingResponse call(ProviderEmbeddingRequest request);
}
