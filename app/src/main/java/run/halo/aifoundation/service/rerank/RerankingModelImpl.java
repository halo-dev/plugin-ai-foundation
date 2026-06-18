package run.halo.aifoundation.service.rerank;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.exception.RerankCancelledException;
import run.halo.aifoundation.exception.RerankTimeoutException;
import run.halo.aifoundation.provider.support.ProviderRerankingClient;
import run.halo.aifoundation.provider.support.RerankingModelProviderOptions;
import run.halo.aifoundation.rerank.RerankDocument;
import run.halo.aifoundation.rerank.RerankRequest;
import run.halo.aifoundation.rerank.RerankResponse;
import run.halo.aifoundation.rerank.RerankWarning;
import run.halo.aifoundation.rerank.RerankingModel;

public class RerankingModelImpl implements RerankingModel {

    private final ProviderRerankingClient client;
    private final String providerType;
    private final RerankingModelProviderOptions providerOptions;

    RerankingModelImpl(ProviderRerankingClient client, String providerType,
        RerankingModelProviderOptions providerOptions) {
        this.client = client;
        this.providerType = providerType;
        this.providerOptions = providerOptions != null
            ? providerOptions
            : RerankingModelProviderOptions.defaults();
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
                return client.rerank(request)
                    .map(response -> withRuntimeWarnings(response, warnings))
                    .doOnNext(response -> checkResultIndexes(request, response));
            })
            .transform(mono -> withRerankTimeout(mono, request))
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
        var warnings = new ArrayList<RerankWarning>();
        if (request.getProviderOptions() != null && !request.getProviderOptions().isEmpty()
            && !providerOptions.isProviderOptionsSupported()) {
            warnings.add(RerankWarning.builder()
                .code("provider-options-not-supported")
                .message("Provider options were supplied but this reranking provider does not declare provider option support.")
                .providerMetadata(Map.of("providerType", providerType))
                .build());
        }
        return warnings;
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
