package run.halo.aifoundation.service;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.aifoundation.EmbeddingModel;
import run.halo.aifoundation.EmbeddingRequest;
import run.halo.aifoundation.EmbeddingResponse;

@Slf4j
public class EmbeddingModelImpl implements EmbeddingModel {

    private final org.springframework.ai.embedding.EmbeddingModel springEmbeddingModel;
    private final String providerType;
    private final int maxEmbeddingsPerCall;
    private final boolean supportsParallelCalls;

    public EmbeddingModelImpl(
        org.springframework.ai.embedding.EmbeddingModel springEmbeddingModel,
        String providerType,
        int maxEmbeddingsPerCall,
        boolean supportsParallelCalls) {
        this.springEmbeddingModel = springEmbeddingModel;
        this.providerType = providerType;
        this.maxEmbeddingsPerCall = maxEmbeddingsPerCall;
        this.supportsParallelCalls = supportsParallelCalls;
    }

    @Override
    public Mono<EmbeddingResponse> embed(List<String> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return Mono.just(EmbeddingResponse.builder().embeddings(List.of()).build());
        }
        var batches = partition(inputs, maxEmbeddingsPerCall > 0 ? maxEmbeddingsPerCall : inputs.size());
        if (supportsParallelCalls) {
            return Flux.fromIterable(batches)
                .flatMap(batch -> Mono.fromCallable(
                    () -> embedBatch(batch, null)).subscribeOn(Schedulers.boundedElastic()))
                .collectList()
                .map(batchResults -> {
                    var all = batchResults.stream()
                        .flatMap(List::stream)
                        .toList();
                    return EmbeddingResponse.builder().embeddings(all).build();
                });
        } else {
            return Flux.fromIterable(batches)
                .concatMap(batch -> Mono.fromCallable(
                    () -> embedBatch(batch, null)).subscribeOn(Schedulers.boundedElastic()))
                .collectList()
                .map(batchResults -> {
                    var all = batchResults.stream()
                        .flatMap(List::stream)
                        .toList();
                    return EmbeddingResponse.builder().embeddings(all).build();
                });
        }
    }

    @Override
    public Mono<EmbeddingResponse> embed(EmbeddingRequest request) {
        if (request == null || request.getInputs() == null || request.getInputs().isEmpty()) {
            return Mono.just(EmbeddingResponse.builder().embeddings(List.of()).build());
        }
        int batchSize = request.getMaxBatchSize() != null && request.getMaxBatchSize() > 0
            ? Math.min(request.getMaxBatchSize(), maxEmbeddingsPerCall > 0 ? maxEmbeddingsPerCall : Integer.MAX_VALUE)
            : (maxEmbeddingsPerCall > 0 ? maxEmbeddingsPerCall : request.getInputs().size());

        var batches = partition(request.getInputs(), batchSize);

        final var springOptions = buildEmbeddingOptions(request);
        if (supportsParallelCalls) {
            return Flux.fromIterable(batches)
                .flatMap(batch -> Mono.fromCallable(
                        () -> embedBatch(batch, springOptions)).subscribeOn(Schedulers.boundedElastic()))
                .collectList()
                .map(batchResults -> {
                    var all = batchResults.stream().flatMap(List::stream).toList();
                    return EmbeddingResponse.builder().embeddings(all).build();
                });
        } else {
            return Flux.fromIterable(batches)
                .concatMap(batch -> Mono.fromCallable(
                        () -> embedBatch(batch, springOptions)).subscribeOn(Schedulers.boundedElastic()))
                .collectList()
                .map(batchResults -> {
                    var all = batchResults.stream().flatMap(List::stream).toList();
                    return EmbeddingResponse.builder().embeddings(all).build();
                });
        }
    }

    @Override
    public Mono<float[]> embedQuery(String text) {
        return Mono.fromCallable(() -> springEmbeddingModel.embed(text))
            .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public int maxEmbeddingsPerCall() {
        return maxEmbeddingsPerCall;
    }

    @Override
    public boolean supportsParallelCalls() {
        return supportsParallelCalls;
    }

    private List<float[]> embedBatch(List<String> batch,
        org.springframework.ai.embedding.EmbeddingOptions options) {
        if (options != null) {
            var request = new org.springframework.ai.embedding.EmbeddingRequest(batch, options);
            var response = springEmbeddingModel.call(request);
            return response.getResults().stream()
                .map(org.springframework.ai.embedding.Embedding::getOutput)
                .toList();
        }
        return springEmbeddingModel.embed(batch);
    }

    private org.springframework.ai.embedding.EmbeddingOptions buildEmbeddingOptions(
        EmbeddingRequest request) {
        // Return null for default options; provider-specific dimensions would be handled here
        return null;
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        if (size <= 0) {
            return List.of(list);
        }
        var result = new ArrayList<List<T>>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }
}
