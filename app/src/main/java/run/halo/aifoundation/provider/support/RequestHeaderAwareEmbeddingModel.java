package run.halo.aifoundation.provider.support;

import java.util.Map;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * Optional adapter contract for embedding models that can apply request-scoped HTTP headers.
 */
public interface RequestHeaderAwareEmbeddingModel {

    EmbeddingResponse call(org.springframework.ai.embedding.EmbeddingRequest request,
        Map<String, String> headers);
}
