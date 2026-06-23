package run.halo.aifoundation.rag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.rerank.RerankDocument;
import run.halo.aifoundation.rerank.RerankRequest;
import run.halo.aifoundation.rerank.RerankResult;
import run.halo.aifoundation.rerank.RerankingModel;
import run.halo.aifoundation.source.RetrievedSource;

/**
 * {@link RagSourceReranker} adapter backed by a provider-neutral {@link RerankingModel}.
 */
public class RerankingModelRagSourceReranker implements RagSourceReranker {

    public static final String RERANK_SCORE_KEY = "rerankScore";
    public static final String RERANK_PROVIDER_METADATA_KEY = "rerankProviderMetadata";

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
                .map(result -> withRerankMetadata(request.getSources(), result))
                .toList());
    }

    private RetrievedSource withRerankMetadata(List<RetrievedSource> sources, RerankResult result) {
        var index = result.getIndex();
        if (index < 0 || index >= sources.size()) {
            throw new IllegalStateException("Rerank result index is outside submitted sources: "
                + index);
        }
        var source = sources.get(index);
        var metadata = new LinkedHashMap<String, Object>();
        if (source.getMetadata() != null) {
            metadata.putAll(source.getMetadata());
        }
        if (result.getScore() != null) {
            metadata.put(RERANK_SCORE_KEY, result.getScore());
        }
        if (result.getProviderMetadata() != null && !result.getProviderMetadata().isEmpty()) {
            metadata.put(RERANK_PROVIDER_METADATA_KEY, Map.copyOf(result.getProviderMetadata()));
        }
        return RetrievedSource.builder()
            .id(source.getId())
            .sourceType(source.getSourceType())
            .title(source.getTitle())
            .url(source.getUrl())
            .content(source.getContent())
            .score(source.getScore())
            .metadata(metadata.isEmpty() ? Map.of() : Map.copyOf(metadata))
            .usedForContext(source.getUsedForContext())
            .visible(source.getVisible())
            .build();
    }
}
