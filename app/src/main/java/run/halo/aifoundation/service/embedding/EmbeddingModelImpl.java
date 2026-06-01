package run.halo.aifoundation.service.embedding;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;
import run.halo.aifoundation.exception.EmbeddingCancelledException;
import run.halo.aifoundation.embedding.EmbeddingErrorEvent;
import run.halo.aifoundation.embedding.EmbeddingFinishEvent;
import run.halo.aifoundation.embedding.EmbeddingLifecycle;
import run.halo.aifoundation.embedding.EmbeddingModel;
import run.halo.aifoundation.embedding.EmbeddingRequest;
import run.halo.aifoundation.embedding.EmbeddingResponse;
import run.halo.aifoundation.embedding.EmbeddingStartEvent;
import run.halo.aifoundation.exception.EmbeddingTimeoutException;
import run.halo.aifoundation.embedding.EmbeddingWarning;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.provider.support.RequestHeaderAwareEmbeddingModel;

@Slf4j
public class EmbeddingModelImpl implements EmbeddingModel {

    private final org.springframework.ai.embedding.EmbeddingModel springEmbeddingModel;
    private final String providerType;
    private final int maxEmbeddingsPerCall;
    private final boolean supportsParallelCalls;
    private final EmbeddingModelProviderOptions providerOptions;
    private final EmbeddingBatchPlanner batchPlanner;
    private final EmbeddingResponseAggregator responseAggregator;

    public EmbeddingModelImpl(
        org.springframework.ai.embedding.EmbeddingModel springEmbeddingModel,
        String providerType,
        int maxEmbeddingsPerCall,
        boolean supportsParallelCalls) {
        this(springEmbeddingModel, providerType, maxEmbeddingsPerCall, supportsParallelCalls,
            EmbeddingModelProviderOptions.defaults(providerType));
    }

    public EmbeddingModelImpl(
        org.springframework.ai.embedding.EmbeddingModel springEmbeddingModel,
        String providerType,
        int maxEmbeddingsPerCall,
        boolean supportsParallelCalls,
        EmbeddingModelProviderOptions providerOptions) {
        this.springEmbeddingModel = springEmbeddingModel;
        this.providerType = providerType;
        this.maxEmbeddingsPerCall = maxEmbeddingsPerCall;
        this.supportsParallelCalls = supportsParallelCalls;
        this.providerOptions = providerOptions != null
            ? providerOptions
            : EmbeddingModelProviderOptions.defaults(providerType);
        this.batchPlanner = new EmbeddingBatchPlanner(maxEmbeddingsPerCall, supportsParallelCalls);
        this.responseAggregator = new EmbeddingResponseAggregator(providerType);
    }

    @Override
    public Mono<EmbeddingResponse> embed(List<String> inputs) {
        return embed(EmbeddingRequest.builder().inputs(inputs).build());
    }

    @Override
    public Mono<EmbeddingResponse> embed(EmbeddingRequest request) {
        var lifecycle = request != null ? request.getLifecycle() : null;
        return Mono.defer(() -> {
                validateRequest(request);
                checkCancellation(request);
                if (request == null || request.getInputs() == null || request.getInputs().isEmpty()) {
                    var empty = EmbeddingResponse.builder()
                        .embeddings(List.of())
                        .warnings(List.of())
                        .providerMetadata(Map.of("providerType", providerType))
                        .build();
                    return invokeStart(lifecycle, request)
                        .then(invokeFinish(lifecycle, request, empty))
                        .thenReturn(empty);
                }

                var warnings = new ArrayList<EmbeddingWarning>();
                warnings.addAll(requestWarnings(request));
                var springOptions = providerOptions.buildOptions(request, warnings);
                var batches = batchPlanner.indexedBatches(request);
                var concurrency = batchPlanner.concurrency(request);
                var batchFlux = Flux.fromIterable(batches);
                Flux<EmbeddingBatchResult> results = supportsParallelCalls && concurrency > 1
                    ? batchFlux.flatMap(batch -> batchCall(request, batch, springOptions), concurrency)
                    : batchFlux.concatMap(batch -> batchCall(request, batch, springOptions));

                return invokeStart(lifecycle, request)
                    .thenMany(results)
                    .collectList()
                    .map(batchResults -> responseAggregator.aggregate(batchResults, warnings))
                    .flatMap(response -> invokeFinish(lifecycle, request, response).thenReturn(response));
            })
            .transform(mono -> withEmbeddingTimeout(mono, request))
            .onErrorResume(error -> invokeError(lifecycle, request, error).then(Mono.error(error)));
    }

    @Override
    public Mono<float[]> embedQuery(String text) {
        return embed(EmbeddingRequest.builder().inputs(List.of(text)).build())
            .map(response -> response.getEmbeddings().get(0));
    }

    @Override
    public int maxEmbeddingsPerCall() {
        return maxEmbeddingsPerCall;
    }

    @Override
    public boolean supportsParallelCalls() {
        return supportsParallelCalls;
    }

    private Mono<EmbeddingBatchResult> batchCall(EmbeddingRequest request,
        EmbeddingBatchPlanner.IndexedBatch batch,
        EmbeddingOptions options) {
        var call = Mono.fromCallable(() -> {
                checkCancellation(request);
                return embedBatch(request, batch, options);
            })
            .subscribeOn(Schedulers.boundedElastic())
            .transform(mono -> withEmbeddingTimeout(mono, request));

        var maxRetries = batchPlanner.maxRetries(request);
        if (maxRetries <= 0) {
            return call;
        }
        return call.retryWhen(Retry.max(maxRetries).filter(this::isRetryable));
    }

    private EmbeddingBatchResult embedBatch(EmbeddingRequest request,
        EmbeddingBatchPlanner.IndexedBatch batch,
        EmbeddingOptions options) {
        var springRequest =
            new org.springframework.ai.embedding.EmbeddingRequest(batch.inputs(), options);
        var response = request.getHeaders() != null && !request.getHeaders().isEmpty()
            && springEmbeddingModel instanceof RequestHeaderAwareEmbeddingModel headerAware
            ? headerAware.call(springRequest, request.getHeaders())
            : springEmbeddingModel.call(springRequest);
        var embeddings = response.getResults().stream()
            .map(org.springframework.ai.embedding.Embedding::getOutput)
            .toList();
        return new EmbeddingBatchResult(batch.index(), embeddings, response.getMetadata());
    }

    private void validateRequest(EmbeddingRequest request) {
        if (request == null) {
            return;
        }
        if (request.getInputs() != null) {
            for (var input : request.getInputs()) {
                if (input == null) {
                    throw new IllegalArgumentException("Embedding inputs must not contain null");
                }
            }
        }
        if (request.getDimensions() != null && request.getDimensions() <= 0) {
            throw new IllegalArgumentException("Embedding dimensions must be positive");
        }
        if (request.getMaxBatchSize() != null && request.getMaxBatchSize() <= 0) {
            throw new IllegalArgumentException("Embedding maxBatchSize must be positive");
        }
        if (request.getMaxParallelCalls() != null && request.getMaxParallelCalls() <= 0) {
            throw new IllegalArgumentException("Embedding maxParallelCalls must be positive");
        }
        if (request.getMaxRetries() != null && request.getMaxRetries() < 0) {
            throw new IllegalArgumentException("Embedding maxRetries must not be negative");
        }
        if (request.getHeaders() != null && !request.getHeaders().isEmpty()
            && !(springEmbeddingModel instanceof RequestHeaderAwareEmbeddingModel)) {
            throw new IllegalArgumentException("Embedding request headers are not supported by provider type: "
                + providerType);
        }
    }

    private List<EmbeddingWarning> requestWarnings(EmbeddingRequest request) {
        var warnings = new ArrayList<EmbeddingWarning>();
        if (request != null && request.getMaxParallelCalls() != null && !supportsParallelCalls) {
            warnings.add(EmbeddingWarning.builder()
                .code("parallel-calls-not-supported")
                .message("The provider does not support parallel embedding calls; calls will run sequentially.")
                .providerMetadata(Map.of("providerType", providerType))
                .build());
        }
        return warnings;
    }

    private boolean isRetryable(Throwable error) {
        return !(error instanceof IllegalArgumentException)
            && !(error instanceof EmbeddingCancelledException);
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

}
