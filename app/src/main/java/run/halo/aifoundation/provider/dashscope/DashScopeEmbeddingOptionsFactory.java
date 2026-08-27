package run.halo.aifoundation.provider.dashscope;

import java.util.List;
import run.halo.aifoundation.embedding.EmbeddingRequest;
import run.halo.aifoundation.embedding.EmbeddingWarning;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;

final class DashScopeEmbeddingOptionsFactory {

    private DashScopeEmbeddingOptionsFactory() {
    }

    static org.springframework.ai.embedding.EmbeddingOptions build(EmbeddingRequest request,
        EmbeddingModelProviderOptions ignored, List<EmbeddingWarning> warnings) {
        if (request == null) {
            return null;
        }
        return DashScopeEmbeddingOptions.builder()
            .dimensions(request.getDimensions())
            .outputType(DashScopeEmbeddingOptions.OutputType.DENSE)
            .customHeaders(request.getHeaders())
            .build();
    }
}
