package run.halo.aifoundation.provider.support;

import java.util.List;
import org.springframework.ai.embedding.EmbeddingOptions;
import run.halo.aifoundation.embedding.EmbeddingRequest;
import run.halo.aifoundation.embedding.EmbeddingWarning;

@FunctionalInterface
public interface EmbeddingOptionsFactory {

    EmbeddingOptions build(EmbeddingRequest request, EmbeddingModelProviderOptions providerOptions,
        List<EmbeddingWarning> warnings);
}
