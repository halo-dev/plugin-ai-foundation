package run.halo.aifoundation;

import java.util.List;
import reactor.core.publisher.Mono;

public interface EmbeddingModel {

    Mono<EmbeddingResponse> embed(List<String> inputs);

    Mono<EmbeddingResponse> embed(EmbeddingRequest request);

    Mono<float[]> embedQuery(String text);

    int maxEmbeddingsPerCall();

    boolean supportsParallelCalls();
}
