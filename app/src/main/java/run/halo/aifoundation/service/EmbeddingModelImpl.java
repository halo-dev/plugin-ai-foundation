package run.halo.aifoundation.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.model.ResponseMetadata;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;
import run.halo.aifoundation.EmbeddingCancelledException;
import run.halo.aifoundation.EmbeddingErrorEvent;
import run.halo.aifoundation.EmbeddingFinishEvent;
import run.halo.aifoundation.EmbeddingLifecycle;
import run.halo.aifoundation.EmbeddingModel;
import run.halo.aifoundation.EmbeddingRequest;
import run.halo.aifoundation.EmbeddingResponse;
import run.halo.aifoundation.EmbeddingStartEvent;
import run.halo.aifoundation.EmbeddingTimeoutException;
import run.halo.aifoundation.EmbeddingUsage;
import run.halo.aifoundation.EmbeddingWarning;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;

@Slf4j
public class EmbeddingModelImpl implements EmbeddingModel {

    private static final int DEFAULT_MAX_RETRIES = 2;

    private final org.springframework.ai.embedding.EmbeddingModel springEmbeddingModel;
    private final String providerType;
    private final int maxEmbeddingsPerCall;
    private final boolean supportsParallelCalls;
    private final EmbeddingModelProviderOptions providerOptions;

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
                var batches = indexedBatches(request);
                var concurrency = concurrency(request);
                var batchFlux = Flux.fromIterable(batches);
                Flux<BatchResult> results = supportsParallelCalls && concurrency > 1
                    ? batchFlux.flatMap(batch -> batchCall(request, batch, springOptions), concurrency)
                    : batchFlux.concatMap(batch -> batchCall(request, batch, springOptions));

                return invokeStart(lifecycle, request)
                    .thenMany(results)
                    .collectList()
                    .map(batchResults -> aggregate(batchResults, warnings))
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

    private Mono<BatchResult> batchCall(EmbeddingRequest request, IndexedBatch batch,
        EmbeddingOptions options) {
        var call = Mono.fromCallable(() -> {
                checkCancellation(request);
                return embedBatch(batch, options);
            })
            .subscribeOn(Schedulers.boundedElastic())
            .transform(mono -> withEmbeddingTimeout(mono, request));

        var maxRetries = maxRetries(request);
        if (maxRetries <= 0) {
            return call;
        }
        return call.retryWhen(Retry.max(maxRetries).filter(this::isRetryable));
    }

    private BatchResult embedBatch(IndexedBatch batch, EmbeddingOptions options) {
        var request = new org.springframework.ai.embedding.EmbeddingRequest(batch.inputs(), options);
        var response = springEmbeddingModel.call(request);
        var embeddings = response.getResults().stream()
            .map(org.springframework.ai.embedding.Embedding::getOutput)
            .toList();
        return new BatchResult(batch.index(), embeddings, response.getMetadata());
    }

    private EmbeddingResponse aggregate(List<BatchResult> batchResults,
        List<EmbeddingWarning> requestWarnings) {
        var sorted = batchResults.stream()
            .sorted(Comparator.comparingInt(BatchResult::index))
            .toList();
        var embeddings = sorted.stream()
            .flatMap(batch -> batch.embeddings().stream())
            .toList();
        var warnings = new ArrayList<>(requestWarnings);
        var usageAccumulator = new UsageAccumulator();
        ResponseMetadata lastMetadata = null;
        var batchMetadata = new ArrayList<Map<String, Object>>();

        for (var batch : sorted) {
            lastMetadata = batch.metadata();
            var usage = usage(batch.metadata());
            if (usage != null) {
                usageAccumulator.add(usage);
            }
            batchMetadata.add(Map.of(
                "index", batch.index(),
                "response", responseMetadataMap(batch.metadata())
            ));
        }

        var usage = usageAccumulator.usage();
        if (usage == null) {
            warnings.add(warning("missing-embedding-usage",
                "The provider response did not include embedding token usage."));
        }

        return EmbeddingResponse.builder()
            .embeddings(embeddings)
            .usage(usage)
            .response(mapResponseMetadata(lastMetadata))
            .warnings(List.copyOf(warnings))
            .providerMetadata(Map.of(
                "providerType", providerType,
                "batches", List.copyOf(batchMetadata)
            ))
            .build();
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
    }

    private List<EmbeddingWarning> requestWarnings(EmbeddingRequest request) {
        var warnings = new ArrayList<EmbeddingWarning>();
        if (request != null && request.getHeaders() != null && !request.getHeaders().isEmpty()
            && !providerOptions.requestHeadersSupported()) {
            warnings.add(EmbeddingWarning.builder()
                .code("unsupported-request-headers")
                .message("Request-scoped embedding headers are not supported by this provider.")
                .providerMetadata(Map.of("providerType", providerType))
                .build());
        }
        if (request != null && request.getMaxParallelCalls() != null && !supportsParallelCalls) {
            warnings.add(EmbeddingWarning.builder()
                .code("parallel-calls-not-supported")
                .message("The provider does not support parallel embedding calls; calls will run sequentially.")
                .providerMetadata(Map.of("providerType", providerType))
                .build());
        }
        return warnings;
    }

    private List<IndexedBatch> indexedBatches(EmbeddingRequest request) {
        var batchSize = batchSize(request);
        var batches = new ArrayList<IndexedBatch>();
        var inputs = request.getInputs();
        for (int index = 0, start = 0; start < inputs.size(); index++, start += batchSize) {
            batches.add(new IndexedBatch(index, inputs.subList(start,
                Math.min(start + batchSize, inputs.size()))));
        }
        return batches;
    }

    private int batchSize(EmbeddingRequest request) {
        if (request.getMaxBatchSize() != null) {
            return Math.min(request.getMaxBatchSize(),
                maxEmbeddingsPerCall > 0 ? maxEmbeddingsPerCall : Integer.MAX_VALUE);
        }
        return maxEmbeddingsPerCall > 0 ? maxEmbeddingsPerCall : request.getInputs().size();
    }

    private int concurrency(EmbeddingRequest request) {
        if (!supportsParallelCalls) {
            return 1;
        }
        var requested = request != null ? request.getMaxParallelCalls() : null;
        if (requested != null) {
            return requested;
        }
        return Integer.MAX_VALUE;
    }

    private int maxRetries(EmbeddingRequest request) {
        return request != null && request.getMaxRetries() != null
            ? request.getMaxRetries()
            : DEFAULT_MAX_RETRIES;
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

    private EmbeddingUsage mapUsage(Usage usage) {
        if (usage == null) {
            return null;
        }
        var total = usage.getTotalTokens();
        if (total == null) {
            var prompt = usage.getPromptTokens();
            var completion = usage.getCompletionTokens();
            if (prompt != null || completion != null) {
                total = safe(prompt) + safe(completion);
            }
        }
        if (total == null && usage.getNativeUsage() == null) {
            return null;
        }
        return EmbeddingUsage.builder()
            .tokens(total)
            .raw(usage.getNativeUsage())
            .build();
    }

    private Usage usage(ResponseMetadata metadata) {
        if (metadata instanceof EmbeddingResponseMetadata embeddingMetadata) {
            return embeddingMetadata.getUsage();
        }
        return null;
    }

    private run.halo.aifoundation.EmbeddingResponseMetadata mapResponseMetadata(
        ResponseMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        var values = responseMetadataMap(metadata);
        return run.halo.aifoundation.EmbeddingResponseMetadata.builder()
            .id(stringValue(values.get("id")))
            .model(model(metadata))
            .timestamp(Instant.now())
            .metadata(values)
            .build();
    }

    private String model(ResponseMetadata metadata) {
        if (metadata instanceof EmbeddingResponseMetadata embeddingMetadata) {
            return embeddingMetadata.getModel();
        }
        var value = metadata.get("model");
        return stringValue(value);
    }

    private Map<String, Object> responseMetadataMap(ResponseMetadata metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        var values = new LinkedHashMap<String, Object>();
        for (var entry : metadata.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                values.put(entry.getKey(), entry.getValue());
            }
        }
        if (metadata instanceof EmbeddingResponseMetadata embeddingMetadata) {
            if (embeddingMetadata.getModel() != null) {
                values.put("model", embeddingMetadata.getModel());
            }
            if (embeddingMetadata.getUsage() != null) {
                values.put("usage", embeddingMetadata.getUsage());
            }
        }
        return Map.copyOf(values);
    }

    private EmbeddingWarning warning(String code, String message) {
        return EmbeddingWarning.builder()
            .code(code)
            .message(message)
            .providerMetadata(Map.of("providerType", providerType))
            .build();
    }

    private Integer safe(Integer value) {
        return value != null ? value : 0;
    }

    private String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }

    private record IndexedBatch(int index, List<String> inputs) {
    }

    private record BatchResult(int index, List<float[]> embeddings, ResponseMetadata metadata) {
    }

    private final class UsageAccumulator {
        private Integer tokens;
        private Object raw;

        void add(Usage usage) {
            var mapped = mapUsage(usage);
            if (mapped == null) {
                return;
            }
            if (mapped.getTokens() != null) {
                tokens = safe(tokens) + mapped.getTokens();
            }
            if (mapped.getRaw() != null) {
                raw = mapped.getRaw();
            }
        }

        EmbeddingUsage usage() {
            if (tokens == null && raw == null) {
                return null;
            }
            return EmbeddingUsage.builder()
                .tokens(tokens)
                .raw(raw)
                .build();
        }
    }
}
