package run.halo.aifoundation.service;

import java.util.ArrayList;
import java.util.List;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.aifoundation.EmbeddingCancelledException;
import run.halo.aifoundation.EmbeddingErrorEvent;
import run.halo.aifoundation.EmbeddingFinishEvent;
import run.halo.aifoundation.EmbeddingLifecycle;
import run.halo.aifoundation.EmbeddingModel;
import run.halo.aifoundation.EmbeddingRequest;
import run.halo.aifoundation.EmbeddingResponse;
import run.halo.aifoundation.EmbeddingStartEvent;
import run.halo.aifoundation.EmbeddingTimeoutException;

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
        var lifecycle = request != null ? request.getLifecycle() : null;
        return Mono.defer(() -> {
                checkCancellation(request);
                if (request == null || request.getInputs() == null || request.getInputs().isEmpty()) {
                    var empty = EmbeddingResponse.builder().embeddings(List.of()).build();
                    return invokeStart(lifecycle, request)
                        .then(invokeFinish(lifecycle, request, empty))
                        .thenReturn(empty);
                }
                int batchSize = request.getMaxBatchSize() != null && request.getMaxBatchSize() > 0
                    ? Math.min(request.getMaxBatchSize(),
                        maxEmbeddingsPerCall > 0 ? maxEmbeddingsPerCall : Integer.MAX_VALUE)
                    : (maxEmbeddingsPerCall > 0 ? maxEmbeddingsPerCall : request.getInputs().size());

                var batches = partition(request.getInputs(), batchSize);
                final var springOptions = buildEmbeddingOptions(request);
                var batchFlux = Flux.fromIterable(batches)
                    .concatMap(batch -> Mono.fromCallable(() -> {
                            checkCancellation(request);
                            return embedBatch(batch, springOptions);
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .transform(mono -> withEmbeddingTimeout(mono, request)));
                if (supportsParallelCalls) {
                    batchFlux = Flux.fromIterable(batches)
                        .flatMap(batch -> Mono.fromCallable(() -> {
                                checkCancellation(request);
                                return embedBatch(batch, springOptions);
                            })
                            .subscribeOn(Schedulers.boundedElastic())
                            .transform(mono -> withEmbeddingTimeout(mono, request)));
                }
                return invokeStart(lifecycle, request)
                    .thenMany(batchFlux)
                    .collectList()
                    .map(batchResults -> {
                        var all = batchResults.stream().flatMap(List::stream).toList();
                        return EmbeddingResponse.builder().embeddings(all).build();
                    })
                    .flatMap(response -> invokeFinish(lifecycle, request, response).thenReturn(response));
            })
            .transform(mono -> withEmbeddingTimeout(mono, request))
            .onErrorResume(error -> invokeError(lifecycle, request, error).then(Mono.error(error)));
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
        if (request.getDimensions() == null) {
            return null;
        }
        return new org.springframework.ai.embedding.EmbeddingOptions() {
            @Override
            public String getModel() {
                return null;
            }

            @Override
            public Integer getDimensions() {
                return request.getDimensions();
            }
        };
    }

    private void checkCancellation(EmbeddingRequest request) {
        if (request != null && request.getCancellationToken() != null
            && request.getCancellationToken().isCancellationRequested()) {
            throw new EmbeddingCancelledException("Embedding was cancelled");
        }
    }

    private <T> Mono<T> withEmbeddingTimeout(Mono<T> mono, EmbeddingRequest request) {
        var timeout = timeout(request);
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return mono;
        }
        return mono.timeout(timeout)
            .onErrorMap(TimeoutException.class, error -> new EmbeddingTimeoutException(timeout, error));
    }

    private Duration timeout(EmbeddingRequest request) {
        return request != null && request.getTimeouts() != null
            ? request.getTimeouts().getTotalTimeout()
            : null;
    }

    private Mono<Void> invokeStart(EmbeddingLifecycle lifecycle, EmbeddingRequest request) {
        if (lifecycle == null) {
            return Mono.empty();
        }
        return lifecycle.onStart(EmbeddingStartEvent.builder()
                .request(request)
                .inputs(request != null && request.getInputs() != null
                    ? List.copyOf(request.getInputs())
                    : List.of())
                .metadata(metadata(request))
                .context(context(request))
                .build())
            .onErrorResume(error -> Mono.empty());
    }

    private Mono<Void> invokeFinish(EmbeddingLifecycle lifecycle, EmbeddingRequest request,
        EmbeddingResponse response) {
        if (lifecycle == null) {
            return Mono.empty();
        }
        return lifecycle.onFinish(EmbeddingFinishEvent.builder()
                .response(response)
                .embeddingsCount(response != null && response.getEmbeddings() != null
                    ? response.getEmbeddings().size()
                    : 0)
                .metadata(metadata(request))
                .context(context(request))
                .build())
            .onErrorResume(error -> Mono.empty());
    }

    private Mono<Void> invokeError(EmbeddingLifecycle lifecycle, EmbeddingRequest request,
        Throwable error) {
        if (lifecycle == null) {
            return Mono.empty();
        }
        return lifecycle.onError(EmbeddingErrorEvent.builder()
                .error(error)
                .metadata(metadata(request))
                .context(context(request))
                .build())
            .onErrorResume(ignored -> Mono.empty());
    }

    private Map<String, Object> metadata(EmbeddingRequest request) {
        return request != null && request.getMetadata() != null
            ? Map.copyOf(request.getMetadata())
            : Map.of();
    }

    private Map<String, Object> context(EmbeddingRequest request) {
        return request != null && request.getContext() != null
            ? Map.copyOf(request.getContext())
            : Map.of();
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Batch size must be positive, got: " + size);
        }
        var result = new ArrayList<List<T>>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }
}
