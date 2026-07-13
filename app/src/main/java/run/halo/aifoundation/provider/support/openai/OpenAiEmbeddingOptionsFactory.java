package run.halo.aifoundation.provider.support.openai;

import java.util.List;
import java.util.Map;
import run.halo.aifoundation.embedding.EmbeddingRequest;
import run.halo.aifoundation.embedding.EmbeddingWarning;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;

public final class OpenAiEmbeddingOptionsFactory {

    private OpenAiEmbeddingOptionsFactory() {
    }

    public static org.springframework.ai.embedding.EmbeddingOptions build(EmbeddingRequest request,
        EmbeddingModelProviderOptions providerOptions, List<EmbeddingWarning> warnings) {
        if (request == null) {
            return null;
        }
        var dimensions = request.getDimensions();

        var headers = request.getHeaders() != null ? request.getHeaders() : Map.<String, String>of();
        if (dimensions == null && headers.isEmpty()) {
            return null;
        }
        var builder = OpenAiCompatibleEmbeddingOptions.builder()
            .dimensions(dimensions);
        builder.customHeaders(headers);
        return builder.build();
    }

}
