package run.halo.aifoundation.rag;

import java.util.List;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.rerank.RerankDocument;
import run.halo.aifoundation.rerank.RerankRequest;
import run.halo.aifoundation.rerank.RerankingModel;
import run.halo.aifoundation.source.RetrievedSource;

/**
 * {@link RagSourceReranker} adapter backed by a provider-neutral {@link RerankingModel}.
 */
public class RerankingModelRagSourceReranker implements RagSourceReranker {

    private final RerankingModel model;

    public RerankingModelRagSourceReranker(RerankingModel model) {
        this.model = model;
    }

    @Override
    public Mono<List<RetrievedSource>> rerank(RagSourceRerankRequest request) {
        var documents = request.getSources().stream()
            .map(source -> RerankDocument.builder()
                .id(source.getId())
                .text(source.getContent())
                .metadata(source.getMetadata())
                .build())
            .toList();
        return model.rerank(RerankRequest.builder()
                .query(request.getQuery())
                .documents(documents)
                .topN(request.getTopN())
                .metadata(request.getMetadata())
                .context(request.getContext())
                .build())
            .map(response -> response.getResults().stream()
                .map(result -> request.getSources().get(result.getIndex()))
                .toList());
    }
}
