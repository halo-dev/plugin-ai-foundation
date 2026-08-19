package run.halo.aifoundation.service.rerank;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.exception.RerankCancelledException;
import run.halo.aifoundation.exception.RerankTimeoutException;
import run.halo.aifoundation.provider.support.ProviderRerankingClient;
import run.halo.aifoundation.provider.support.RerankingModelProviderOptions;
import run.halo.aifoundation.provider.mapping.EffectiveParameterMappings;
import run.halo.aifoundation.provider.mapping.ModelParameter;
import run.halo.aifoundation.provider.mapping.ParameterMappingTarget;
import run.halo.aifoundation.provider.mapping.RuntimeParameterMappings;
import run.halo.aifoundation.rerank.RerankDocument;
import run.halo.aifoundation.rerank.RerankRequest;
import run.halo.aifoundation.rerank.RerankResponse;
import run.halo.aifoundation.rerank.RerankWarning;
import run.halo.aifoundation.rerank.RerankingModel;
import run.halo.aifoundation.service.model.ModelRuntimeContext;
import run.halo.aifoundation.service.usage.NormalizedUsage;
import run.halo.aifoundation.service.usage.UsageExecutionObserver;
import run.halo.aifoundation.service.usage.UsageUnitKind;

public class RerankingModelImpl implements RerankingModel {

    private final ProviderRerankingClient client;
    private final String providerType;
    private final RerankingModelProviderOptions providerOptions;
    private final RuntimeParameterMappings parameterMappings;
    private final UsageExecutionObserver usageExecutionObserver;

    RerankingModelImpl(ProviderRerankingClient client, String providerType,
        RerankingModelProviderOptions providerOptions) {
        this(client, providerType, providerOptions, EffectiveParameterMappings.empty());
    }

    RerankingModelImpl(ProviderRerankingClient client, String providerType,
        RerankingModelProviderOptions providerOptions,
        EffectiveParameterMappings parameterMappings) {
        this(client, providerType, providerOptions, parameterMappings, null, null);
    }

    RerankingModelImpl(ProviderRerankingClient client, String providerType,
        RerankingModelProviderOptions providerOptions,
        EffectiveParameterMappings parameterMappings, String modelName, String providerName) {
        this(client, providerOptions,
            ModelRuntimeContext.unresolved(providerType, modelName, providerName,
                new RuntimeParameterMappings(parameterMappings, null, modelName, providerName)));
    }

    RerankingModelImpl(ProviderRerankingClient client,
        RerankingModelProviderOptions providerOptions, ModelRuntimeContext context) {
        this(client, providerOptions, context, null);
    }

    RerankingModelImpl(ProviderRerankingClient client,
        RerankingModelProviderOptions providerOptions, ModelRuntimeContext context,
        UsageExecutionObserver usageExecutionObserver) {
        this.client = client;
        this.providerType = context.providerType();
        this.providerOptions = providerOptions != null
            ? providerOptions
            : RerankingModelProviderOptions.defaults();
        this.parameterMappings = context.parameterMappings();
        this.usageExecutionObserver = usageExecutionObserver;
    }

    @Override
    public Mono<RerankResponse> rerank(RerankRequest request) {
        return Mono.defer(() -> {
                validateRequest(request);
                checkCancellation(request);
                if (request.getDocuments() == null || request.getDocuments().isEmpty()) {
                    return Mono.just(emptyResponse(request));
                }
                var warnings = requestWarnings(request);
                var target = mappedTopN(request, warnings);
                Supplier<Mono<RerankResponse>> invocation =
                    () -> withRerankTimeout(client.rerank(request, target), request);
                var call = usageExecutionObserver == null ? invocation.get()
                    : usageExecutionObserver.observe(UsageUnitKind.RERANK, 0, invocation,
                        response -> NormalizedUsage.from(response.getUsage()),
                        response -> null);
                return call
                    .map(response -> withRuntimeWarnings(response, warnings))
                    .doOnNext(response -> checkResultIndexes(request, response));
            })
            .doOnNext(ignored -> checkCancellation(request));
    }

    private void validateRequest(RerankRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Rerank request is required");
        }
        if (request.getQuery() == null || request.getQuery().isBlank()) {
            throw new IllegalArgumentException("Rerank query is required");
        }
        if (request.getTopN() != null && request.getTopN() <= 0) {
            throw new IllegalArgumentException("Rerank topN must be positive");
        }
        if (request.getDocuments() != null) {
            for (var document : request.getDocuments()) {
                validateDocument(document);
            }
        }
    }

    private void validateDocument(RerankDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Rerank documents must not contain null");
        }
        if (document.getText() == null) {
            throw new IllegalArgumentException("Rerank document text must not be null");
        }
    }

    private RerankResponse emptyResponse(RerankRequest request) {
        return RerankResponse.builder()
            .query(request.getQuery())
            .results(List.of())
            .warnings(requestWarnings(request))
            .providerMetadata(Map.of("providerType", providerType))
            .build();
    }

    private List<RerankWarning> requestWarnings(RerankRequest request) {
        return new ArrayList<>();
    }

    private ParameterMappingTarget mappedTopN(RerankRequest request,
        List<RerankWarning> warnings) {
        if (request.getTopN() == null) {
            return null;
        }
        if (parameterMappings.get(ModelParameter.TOP_N) == null) {
            return null;
        }
        var target = new ParameterMappingTarget();
        if (parameterMappings.isUnsupported(ModelParameter.TOP_N)) {
            warnings.add(parameterMappings.unsupportedDiagnostic(ModelParameter.TOP_N)
                .rerankWarning());
            return target;
        }
        parameterMappings.apply(ModelParameter.TOP_N, request.getTopN(), target);
        return target;
    }

    private RerankResponse withRuntimeWarnings(RerankResponse response,
        List<RerankWarning> warnings) {
        if (warnings.isEmpty()) {
            return response;
        }
        var allWarnings = new ArrayList<RerankWarning>();
        if (response.getWarnings() != null) {
            allWarnings.addAll(response.getWarnings());
        }
        allWarnings.addAll(warnings);
        response.setWarnings(List.copyOf(allWarnings));
        return response;
    }

    private void checkResultIndexes(RerankRequest request, RerankResponse response) {
        if (response == null || response.getResults() == null) {
            return;
        }
        var size = request.getDocuments().size();
        for (var result : response.getResults()) {
            if (result.getIndex() < 0 || result.getIndex() >= size) {
                throw new IllegalArgumentException("Rerank result index is out of range: "
                    + result.getIndex());
            }
        }
    }

    private void checkCancellation(RerankRequest request) {
        if (request.getCancellationToken() != null
            && request.getCancellationToken().isCancellationRequested()) {
            throw new RerankCancelledException("Reranking was cancelled");
        }
    }

    private <T> Mono<T> withRerankTimeout(Mono<T> mono, RerankRequest request) {
        var timeout = timeout(request);
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return mono;
        }
        return mono.timeout(timeout)
            .onErrorMap(TimeoutException.class, error -> new RerankTimeoutException(timeout,
                error));
    }

    private Duration timeout(RerankRequest request) {
        return request.getTimeouts() != null ? request.getTimeouts().getTotalTimeout() : null;
    }
}
