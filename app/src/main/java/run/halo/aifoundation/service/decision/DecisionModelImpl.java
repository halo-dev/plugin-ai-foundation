package run.halo.aifoundation.service.decision;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.capability.DecisionCapability;
import run.halo.aifoundation.decision.DecisionModel;
import run.halo.aifoundation.decision.DecisionRequest;
import run.halo.aifoundation.decision.DecisionResponse;
import run.halo.aifoundation.exception.DecisionCancelledException;
import run.halo.aifoundation.exception.DecisionTimeoutException;
import run.halo.aifoundation.provider.support.DecisionResponseValidationException;
import run.halo.aifoundation.provider.support.ProviderDecisionClient;
import run.halo.aifoundation.service.observation.NormalizedUsage;
import run.halo.aifoundation.service.observation.UsageExecutionObserver;
import run.halo.aifoundation.service.observation.UsageUnitKind;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

public class DecisionModelImpl implements DecisionModel {
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    private final ProviderDecisionClient client;
    private final UsageExecutionObserver observer;
    private final DecisionCapability capabilities;

    public DecisionModelImpl(
            ProviderDecisionClient client, UsageExecutionObserver observer, DecisionCapability capabilities) {
        this.client = client;
        this.observer = observer;
        this.capabilities = copyCapabilities(capabilities);
    }

    @Override
    public DecisionCapability capabilities() {
        return copyCapabilities(capabilities);
    }

    private static DecisionCapability copyCapabilities(DecisionCapability capabilities) {
        return capabilities == null
                ? null
                : new DecisionCapability(
                        capabilities.getQuestionTypes() == null ? null : List.copyOf(capabilities.getQuestionTypes()),
                        capabilities.getStructuredState(),
                        capabilities.getMaxChoiceOptions(),
                        capabilities.getMaxScoreLevels(),
                        capabilities.getChoiceProbabilities(),
                        capabilities.getChoiceConfidence(),
                        capabilities.getScoreProbabilities(),
                        capabilities.getScoreConfidence(),
                        capabilities.getScoreLegend());
    }

    @Override
    public Mono<DecisionResponse> decide(DecisionRequest request) {
        return Mono.defer(() -> {
            DecisionValidation.request(request);
            var snapshot = snapshotRequest(request);
            DecisionValidation.capabilities(snapshot, capabilities);
            checkCancellation(snapshot);
            var timeout = snapshot.getTimeouts() == null
                    ? null
                    : snapshot.getTimeouts().getTotalTimeout();
            return observer.observe(
                            UsageUnitKind.DECISION,
                            0,
                            () -> {
                                var call = client.decide(snapshot)
                                        .switchIfEmpty(Mono.error(new IllegalStateException("Empty decision response")))
                                        .doOnNext(response -> {
                                            try {
                                                DecisionValidation.response(snapshot, response);
                                            } catch (IllegalStateException error) {
                                                throw new DecisionResponseValidationException(
                                                        error, response.getModel(), response.getUsage());
                                            }
                                        });
                                return timeout == null
                                        ? call
                                        : call.timeout(timeout)
                                                .onErrorMap(
                                                        TimeoutException.class,
                                                        error -> new DecisionTimeoutException(timeout, error));
                            },
                            response -> NormalizedUsage.from(response.getUsage()),
                            DecisionResponse::getModel)
                    .doOnNext(response -> checkCancellation(snapshot));
        });
    }

    private static DecisionRequest snapshotRequest(DecisionRequest request) {
        var snapshot = JSON_MAPPER.convertValue(
                Map.of("state", request.getState(), "questions", request.getQuestions()), DecisionRequest.class);
        // Audit metadata and live cancellation controls are local, not part of the JSON payload.
        snapshot.setMetadata(request.getMetadata() == null ? null : new LinkedHashMap<>(request.getMetadata()));
        snapshot.setCancellationToken(request.getCancellationToken());
        snapshot.setTimeouts(request.getTimeouts());
        return snapshot;
    }

    private static void checkCancellation(DecisionRequest request) {
        if (request.getCancellationToken() != null
                && request.getCancellationToken().isCancellationRequested()) {
            throw new DecisionCancelledException();
        }
    }
}
