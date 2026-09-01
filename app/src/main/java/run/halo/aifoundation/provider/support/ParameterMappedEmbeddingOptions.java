package run.halo.aifoundation.provider.support;

import java.util.Map;
import org.springframework.ai.embedding.EmbeddingOptions;

/**
 * Embedding options that can carry administrator-defined request-body fields.
 *
 * <p>Provider adapters opt into this contract only when their transport supports additional
 * JSON fields. Standard parameters remain strongly typed on {@link EmbeddingOptions}.</p>
 */
public interface ParameterMappedEmbeddingOptions extends EmbeddingOptions {

    EmbeddingOptions withMappedBody(Map<String, Object> mappedBody);
}
