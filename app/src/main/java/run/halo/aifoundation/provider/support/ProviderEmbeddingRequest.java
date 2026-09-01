package run.halo.aifoundation.provider.support;

import java.util.List;
import java.util.Map;
import org.springframework.ai.embedding.EmbeddingOptions;
import run.halo.aifoundation.embedding.EmbeddingContent;

/** Provider-facing embedding invocation after common validation and batching. */
public record ProviderEmbeddingRequest(
    List<String> inputs,
    List<EmbeddingContent> contents,
    EmbeddingOptions options,
    Map<String, String> headers
) {
    public ProviderEmbeddingRequest {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        contents = contents == null ? List.of() : List.copyOf(contents);
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
